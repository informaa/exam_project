package com.astanait.universityschedule.controller;

import com.astanait.universityschedule.dto.ExamScheduleEntryDto;
import com.astanait.universityschedule.model.User;
import com.astanait.universityschedule.repository.UserRepository;
import com.astanait.universityschedule.service.ExamScheduleService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/exams")
public class ExamScheduleController {

    private static final Logger log = LoggerFactory.getLogger(ExamScheduleController.class);
    private final ExamScheduleService examService; // сервис для работы с данными экзаменов
    private final UserRepository userRepository; // репозиторий для получения данных о пользователях

    // Конструктор с внедрением зависимостей через `@Autowired` — стандартный способ получить сервисы и репозитории в Spring
    @Autowired
    public ExamScheduleController(ExamScheduleService examService, UserRepository userRepository) {
        this.examService = examService;
        this.userRepository = userRepository;
    }

    // Метод добавляет в модель флаги для определения состояний на сайте
    private void addActivePageAttributes(Model model) {
        model.addAttribute("isSchedulePageActive", false);
        model.addAttribute("isExamsPageActive", true);
    }

    // Вспомогательный метод заполняет модель данными для отображения страницы экзаменов
    private void populateExamPageCommonAttributes(Model model, String currentUserGroupName, boolean isAdmin, ExamScheduleEntryDto formEntry) {
        // Добавляем в модель список ближайших учебных лет, например: `[2025, 2024, 2023, 2022]`
        int currentYear = LocalDate.now().getYear();
        model.addAttribute("academicYears", Arrays.asList(currentYear + 1, currentYear, currentYear - 1, currentYear - 2));
        // Добавляем номера семестров
        model.addAttribute("academicPeriods", Arrays.asList(1, 2));
        // Вызываем уже известный метод, указывая, что это страница экзаменов
        addActivePageAttributes(model);
        // Эти данные используются для ограничения прав: админ видит всё, студент — только свои
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("currentUserGroupName", currentUserGroupName);
        model.addAttribute("availableGroups", List.of("ВТ-23А", "ВТ-23Б", "ПИ-22А", "ИС-21А"));

        // Создаём переменные для данных, передаваемых в форму отмены экзамена
        Integer cancelYear = null;
        Integer cancelPeriod = null;
        String cancelGroupName = null;

        // Если в форме указаны год и период — берем их; группа зависит от прав: у админа своя, у студента — его группа
        if (formEntry != null && formEntry.getAcademicYear() != null && formEntry.getAcademicPeriod() != null) {
            cancelYear = formEntry.getAcademicYear();
            cancelPeriod = formEntry.getAcademicPeriod();
            cancelGroupName = isAdmin ? formEntry.getGroupName() : currentUserGroupName;

            // Если данных нет — ищем их в модели или используем текущие значения (год, семестр и т.д.)
        } else {
            if (model.containsAttribute("selectedYear")) {
                cancelYear = (Integer) model.getAttribute("selectedYear");
            } else {
                cancelYear = LocalDate.now().getYear();
            }
            if (model.containsAttribute("selectedPeriod")) {
                cancelPeriod = (Integer) model.getAttribute("selectedPeriod");
            } else {
                cancelPeriod = 1;
            }
            cancelGroupName = isAdmin ? (String) model.getAttribute("selectedGroupName") : currentUserGroupName;
        }
        // Эти данные используются для предзаполнения полей формы отмены экзамена на странице
        model.addAttribute("cancelFormYear", cancelYear);
        model.addAttribute("cancelFormPeriod", cancelPeriod);
        model.addAttribute("cancelFormGroupName", cancelGroupName);
    }

    @GetMapping
    public String showExamSchedulePage(
            @RequestParam(value = "year", required = false) Integer filterAcademicYear,
            @RequestParam(value = "period", required = false) Integer filterAcademicPeriod,
            @RequestParam(value = "groupName", required = false) String filterGroupNameFromRequest,
            Model model,
            Authentication authentication) {

        // Получаем имя текущего пользователя
        String currentPrincipalName = authentication.getName();
        // Проверяем, является ли пользователь администратором
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_ADMIN"));

        // Ищем в базе данных текущего пользователя и получаем его группу
        String resolvedUserGroupName = null;
        User currentUser = userRepository.findByUsername(currentPrincipalName).orElse(null);
        if (currentUser != null) {
            resolvedUserGroupName = currentUser.getGroupName();
        }

        // Если пользователь — админ, он может выбрать любую группу через `filterGroupNameFromRequest`
        // иначе видит только свою группу
        String groupNameToFilterBy;
        if (isAdmin) {
            groupNameToFilterBy = filterGroupNameFromRequest;
            if (groupNameToFilterBy != null && !groupNameToFilterBy.isBlank()) {
                model.addAttribute("selectedGroupName", groupNameToFilterBy);
            }
        } else {
            groupNameToFilterBy = resolvedUserGroupName;
        }

        // Если год или период не указаны — используем текущий год и первый семестр
        if (filterAcademicYear == null) {
            filterAcademicYear = LocalDate.now().getYear();
        }
        if (filterAcademicPeriod == null) {
            filterAcademicPeriod = 1;
        }
        populateExamPageCommonAttributes(model, resolvedUserGroupName, isAdmin, null);
        // Эти данные используются на странице, например, для подсветки выбранных значений в выпадающих списках
        model.addAttribute("selectedYear", filterAcademicYear);
        model.addAttribute("selectedPeriod", filterAcademicPeriod);

        // Запрашиваем у сервиса список экзаменов, основываясь на фильтрах
        List<ExamScheduleEntryDto> exams = examService.getExamEntries(filterAcademicYear, filterAcademicPeriod, groupNameToFilterBy, isAdmin);
        // Передаём всё в модель и возвращаем шаблон
        model.addAttribute("exams", exams);
        model.addAttribute("pageTitle", "Расписание экзаменов");
        return "exam-schedule";
    }

    // Метод кодирует строку для безопасного использования в URL-параметре
    private String encodeURLParam(String param) {
        if (param == null || param.isBlank()) {
            return "";
        }
        return UriUtils.encode(param, StandardCharsets.UTF_8);
    }

    // Метод строит URL для перенаправления, например, после сохранения формы экзамена
    private String buildExamRedirectUrl(ExamScheduleEntryDto examDto, boolean isAdmin, String activeFilterGroupName) {
        // Получаем из DTO: учебный год, номер семестра и группу (если указана).
        String yearParam = examDto.getAcademicYear() != null ? examDto.getAcademicYear().toString() : "";
        String periodParam = examDto.getAcademicPeriod() != null ? examDto.getAcademicPeriod().toString() : "";
        String groupNameParamValue = "";

        // Если пользователь — админ: берём группу из фильтра, если указана, иначе — из записи
        if (isAdmin) {
            String groupToUse = (activeFilterGroupName != null && !activeFilterGroupName.isBlank()) ?
                    activeFilterGroupName : examDto.getGroupName();
            if (groupToUse != null && !groupToUse.isBlank()) {
                groupNameParamValue = encodeURLParam(groupToUse);
            }
        }

        // Создаём базовый URL и список параметров
        StringBuilder url = new StringBuilder("/exams?");
        List<String> params = new ArrayList<>();

        // Добавляем только те параметры, которые не пустые
        if (!yearParam.isEmpty()) {
            params.add("year=" + yearParam);
        }
        if (!periodParam.isEmpty()) {
            params.add("period=" + periodParam);
        }
        if (isAdmin && !groupNameParamValue.isEmpty()) {
            params.add("groupName=" + groupNameParamValue);
        }

        // Если параметров нет — возвращаем простой адрес /exams
        if (params.isEmpty() && !isAdmin) {
            return "/exams";
        }
        if (params.isEmpty() && isAdmin) {
            return "/exams";
        }
        // Соединяем параметры через & и добавляем к URL
        return url.append(String.join("&", params)).toString();
    }

    @GetMapping("/new")
    public String showNewExamForm(
            // передача данных в шаблон
            // информация о текущем пользователе
            Model model, Authentication authentication,
            @RequestParam(value="activeYear", required = false) Integer activeYear,
            @RequestParam(value="activePeriod", required = false) Integer activePeriod,
            @RequestParam(value="activeGroup", required = false) String activeGroup) {

        // Создаём новый DTO-объект для формы добавления экзамена.
        ExamScheduleEntryDto newExam = new ExamScheduleEntryDto();
        // Проверяем, является ли пользователь администратором
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN"));

        // Получаем группу текущего пользователя, если он найден
        String currentAdminGroupName = null;
        User currentUser = userRepository.findByUsername(authentication.getName()).orElse(null);

        // Если указан год — используем его
        // Иначе — ставим текущий год
        if (currentUser != null) {
            currentAdminGroupName = currentUser.getGroupName();
        }
        if (activeYear != null) {
            newExam.setAcademicYear(activeYear);
        } else {
            newExam.setAcademicYear(LocalDate.now().getYear());
        }
        // Если указан период — используем его
        // Иначе — первый период (семестр)
        if (activePeriod != null) {
            newExam.setAcademicPeriod(activePeriod);
        } else {
            newExam.setAcademicPeriod(1);
        }
        // Если пользователь — админ и указана группа — устанавливаем её в форму
        if (isAdmin && activeGroup != null) {
            newExam.setGroupName(activeGroup);
        }

        // Добавляем форму в модель, чтобы она была доступна в шаблоне
        model.addAttribute("examEntry", newExam);
        // Устанавливаем заголовок страницы
        model.addAttribute("pageTitle", "Добавить запись об экзамене");
        populateExamPageCommonAttributes(model, currentAdminGroupName, isAdmin, newExam);
        return "exam-form";
    }

    @PostMapping("/save")
    public String saveExam(@Valid @ModelAttribute("examEntry") ExamScheduleEntryDto examDto,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes,
                           Model model, Authentication authentication,
                           @RequestParam(value = "activeGroup", required = false) String activeGroupFilter) {
        // Проверяем, является ли пользователь администратором
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN"));

        // Получаем группу текущего пользователя, если он найден
        String currentAdminGroupName = null;
        User currentUser = userRepository.findByUsername(authentication.getName()).orElse(null);
        if (currentUser != null) {
            currentAdminGroupName = currentUser.getGroupName();
        }

        // Если пользователь — админ и не указал группу — добавляем ошибку валидации.
        if (isAdmin && (examDto.getGroupName() == null || examDto.getGroupName().isBlank())) {
            bindingResult.rejectValue("groupName", "error.examEntry", "Пожалуйста, выберите группу для экзамена.");
        }

        // Если есть ошибки валидации: меняем заголовок страницы, перезаполняем модель данными и возвращаем форму с подсвеченными ошибками
        if (bindingResult.hasErrors()) {
            model.addAttribute("pageTitle", "Добавить запись об экзамене (Ошибка!)");
            populateExamPageCommonAttributes(model, currentAdminGroupName, isAdmin, examDto);
            return "exam-form";
        }
        try { // Вызываем сервис для сохранения или обновления записи
            examService.saveOrUpdateExamEntry(examDto);
            redirectAttributes.addFlashAttribute("successMessage", "Запись об экзамене успешно добавлена!");
            return "redirect:" + buildExamRedirectUrl(examDto, isAdmin, activeGroupFilter);
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Ошибка при сохранении экзамена: " + e.getMessage());
            model.addAttribute("pageTitle", "Добавить запись об экзамене (Ошибка!)");
            populateExamPageCommonAttributes(model, currentAdminGroupName, isAdmin, examDto);
            return "exam-form";
        }
    }

    @GetMapping("/edit/{id}")
    public String showEditExamForm(
            @PathVariable Long id, // ID экзамена из URL
            Model model, // чтобы передать данные в шаблон
            RedirectAttributes redirectAttributes, // для передачи сообщений при перенаправлении
            Authentication authentication) { // информация о текущем пользователе

        // Проверяем, является ли пользователь администратором
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN"));

        // Получаем группу текущего пользователя, если он найден
        String currentAdminGroupName = null;
        User currentUser = userRepository.findByUsername(authentication.getName()).orElse(null);

        // Используем сервис, чтобы получить данные экзамена по его ID
        if (currentUser != null) {
            currentAdminGroupName = currentUser.getGroupName();
        }

        try {
            ExamScheduleEntryDto examDto = examService.getExamById(id);
            // Передаём `examDto` в модель, чтобы форма отобразила уже заполненные данные
            model.addAttribute("examEntry", examDto);
            model.addAttribute("pageTitle", "Редактировать запись об экзамене (ID: " + id + ")");
            populateExamPageCommonAttributes(model, currentAdminGroupName, isAdmin, examDto);
            return "exam-form";

            // Если экзамен с таким ID не найден — добавляем ошибку и перенаправляем на главную страницу расписания экзаменов
        } catch (EntityNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Экзамен с ID " + id + " не найден.");
            return "redirect:/exams";
        }
    }

    @PostMapping("/update")
    public String updateExam(@Valid @ModelAttribute("examEntry") ExamScheduleEntryDto examDto,
                             BindingResult bindingResult,
                             RedirectAttributes redirectAttributes,
                             Model model, Authentication authentication,
                             @RequestParam(value = "activeGroup", required = false) String activeGroupFilter) {

        // Проверяем, является ли пользователь администратором
        boolean isAdmin = authentication.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN"));
        // Получаем группу текущего пользователя, если он найден
        String currentAdminGroupName = null;
        User currentUser = userRepository.findByUsername(authentication.getName()).orElse(null);
        if (currentUser != null) {
            currentAdminGroupName = currentUser.getGroupName();
        }

        // Если запись не имеет ID — логируем ошибку, отправляем сообщение и перенаправляем обратно на страницу расписания
        if (examDto.getId() == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка: ID экзамена не указан для обновления.");
            return "redirect:/exams";
        }

        // Если пользователь — админ, но не указал группу — добавляем ошибку валидации
        if (isAdmin && (examDto.getGroupName() == null || examDto.getGroupName().isBlank())) {
            bindingResult.rejectValue("groupName", "error.examEntry", "Пожалуйста, выберите группу для экзамена.");
        }

        // Если есть ошибки валидации — меняем заголовок, перезаполняем модель и возвращаем форму с ошибками
        if (bindingResult.hasErrors()) {
            model.addAttribute("pageTitle", "Редактировать запись об экзамене (ID: " + examDto.getId() + ") (Ошибка!)");
            populateExamPageCommonAttributes(model, currentAdminGroupName, isAdmin, examDto);
            return "exam-form";
        }
        try { // Вызываем сервис для сохранения или обновления записи
            examService.saveOrUpdateExamEntry(examDto);
            redirectAttributes.addFlashAttribute("successMessage", "Запись об экзамене успешно обновлена!");
            // Перенаправляем пользователя обратно на страницу с фильтром, откуда он пришёл
            return "redirect:" + buildExamRedirectUrl(examDto, isAdmin, activeGroupFilter);

            // Если запись не найдена — передаём ошибку и перенаправляем обратно
        } catch (EntityNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка: Экзамен для обновления не найден (ID: " + examDto.getId() + ").");
            return "redirect:/exams";
        }
        // При ошибке — добавляем сообщение, меняем заголовок, перезаполняем модель и возвращаем форму с ошибкой
        catch (Exception e) {
            model.addAttribute("errorMessage", "Ошибка при обновлении экзамена: " + e.getMessage());
            model.addAttribute("pageTitle", "Редактировать запись об экзамене (ID: " + examDto.getId() + ") (Ошибка!)");
            populateExamPageCommonAttributes(model, currentAdminGroupName, isAdmin, examDto);
            return "exam-form";
        }
    }

    @GetMapping("/delete/{id}")
    public String deleteExam(
            @PathVariable Long id, // ID экзамена из URL
            RedirectAttributes redirectAttributes, // для передачи сообщений при перенаправлении
            Authentication authentication, // информация о текущем пользователе
            //  группа из фильтра, чтобы вернуть пользователя туда после удаления
            @RequestParam(value = "activeGroup", required = false) String activeGroupFilter) {
            String targetRedirectUrl = "/exams";

        // Проверяем, является ли пользователь администратором
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN"));
        // Создаём переменную для данных удаляемого экзамена
        ExamScheduleEntryDto examDto = null;
        try { // Получаем запись по ID из сервиса
            examDto = examService.getExamById(id);
            // Вызываем метод удаления записи
            examService.deleteExamEntry(id);
            // Передаём сообщение об успешном удалении
            redirectAttributes.addFlashAttribute("successMessage", "Запись об экзамене ID " + id + " успешно удалена.");
            // Строим URL с фильтрами для перенаправления после удаления
            if (examDto != null) {
                targetRedirectUrl = buildExamRedirectUrl(examDto, isAdmin, activeGroupFilter);
            }
        // Если запись не найдена — отправляем сообщение об ошибке
        } catch (EntityNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Экзамен с ID " + id + " не найден для удаления.");
        // Если произошла любая другая ошибка — передаём краткое описание пользователю
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка при удалении экзамена: " + e.getMessage());
        }
        return "redirect:" + targetRedirectUrl;
    }

    @GetMapping("/export")
    public ResponseEntity<InputStreamResource> exportToExcel(
            @RequestParam(value = "year") Integer academicYear,
            @RequestParam(value = "period") Integer academicPeriod,
            @RequestParam(value = "groupName", required = false) String groupName,
            Authentication authentication) { // информация о текущем пользователе

        // Определяем, является ли пользователь администратором
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_ADMIN"));

        String groupNameToExport;
        if (isAdmin) { // Админ может выбрать любую группу или не указывать её
            groupNameToExport = (groupName != null && !groupName.isBlank()) ? groupName : null;

        } else { // Не админ видит только свою группу; при отсутствии — 400 ошибка
            User currentUser = userRepository.findByUsername(authentication.getName()).orElse(null);
            groupNameToExport = (currentUser != null) ? currentUser.getGroupName() : null;
            if (groupNameToExport == null) {
                log.warn("Attempt to export by student without a group. User: {}", authentication.getName());
                return ResponseEntity.badRequest().body(null);
            }
        }

        // Получаем у сервиса список экзаменов по заданным фильтрам
        List<ExamScheduleEntryDto> exams = examService.getExamEntries(academicYear, academicPeriod, groupNameToExport, isAdmin);
        try { // Передаём список экзаменов в сервис для формирования Excel-файла в памяти
            ByteArrayInputStream byteArrayInputStream = examService.exportExamsToExcel(exams);
            // Формируем понятное имя файла для корректного отображения кириллицы в браузере
            String filenameSuffix = (groupNameToExport != null) ? "_group_" + encodeURLParam(groupNameToExport) : "_all_groups";
            String rawFilename = "exam_schedule_" + academicYear + "_period_" + academicPeriod + filenameSuffix + ".xlsx";
            String encodedFilename = UriUtils.encode(rawFilename, StandardCharsets.UTF_8);

            // Заголовки указывают браузеру
            // это скачиваемый файл (Content-Disposition: attachment)
            // его имя с поддержкой UTF-8
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename);
            log.info("Excel export successful. Filename: {}", rawFilename);

            // Возвращаем Excel-файл как ответ:
            // Тип контента — бинарный поток (application/octet-stream)
            // Содержимое — наш Excel-файл в виде InputStreamResource
            return ResponseEntity
                    .ok()
                    .headers(headers)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(new InputStreamResource(byteArrayInputStream));

            // Если ошибка при создании файла — логируем и возвращаем статус 500 (Internal Server Error)
        } catch (IOException e) {
            log.error("Error exporting exam schedule to Excel: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(null);
        }
    }
}