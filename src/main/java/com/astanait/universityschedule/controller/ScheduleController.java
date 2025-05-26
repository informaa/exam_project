package com.astanait.universityschedule.controller;

import com.astanait.universityschedule.dto.ScheduleEntryDto;
import com.astanait.universityschedule.model.User;
import com.astanait.universityschedule.repository.UserRepository;
import com.astanait.universityschedule.service.ScheduleService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

// Объявление класса и инъекция зависимостей
@Controller
@RequestMapping("/schedule")
public class ScheduleController {

    private static final Logger log = LoggerFactory.getLogger(ScheduleController.class);
    private final ScheduleService scheduleService;
    private final UserRepository userRepository;

    @Autowired
    public ScheduleController(ScheduleService scheduleService, UserRepository userRepository) {
        this.scheduleService = scheduleService;
        this.userRepository = userRepository;
    }

    private void addActivePageAttributes(Model model) {
        model.addAttribute("isSchedulePageActive", true);
        model.addAttribute("isExamsPageActive", false);
    }

    // Метод populateCommonModelAttributes — заполнение общих атрибутов модели
    private void populateCommonModelAttributes(
            Model model, // передача данных в шаблон
            String currentFilterAcademicYear, // текущие фильтры
            Integer currentFilterSemester,
            Integer currentFilterWeek,
            ScheduleEntryDto formEntry, // запись расписания для редактирования или создания
            String currentUserGroupName, // группа пользователя
            boolean isAdmin) { // флаг, является ли пользователь администратором

        model.addAttribute("academicYears", Arrays.asList("2024-2025", "2025-2026", "2026-2027")); // список учебных годов
        model.addAttribute("semesters", Arrays.asList(1, 2)); // список семестров
        List<Integer> weeks = new ArrayList<>();
        for (int i = 1; i <= 15; i++) weeks.add(i); // номера недель (от 1 до 15)
        model.addAttribute("weekNumbers", weeks);
        model.addAttribute("availableGroups", List.of("ВТ-23А", "ВТ-23Б", "ПИ-22А", "ИС-21А")); // Список доступных групп
        model.addAttribute("currentUserGroupName", currentUserGroupName); // Группа текущего пользователя
        model.addAttribute("isAdmin", isAdmin);

        // Работа с датами семестра
        String displaySemesterStartDate = "N/A";
        String displaySemesterEndDate = "N/A";
        // Если в форме указаны год или семестр — используем их. Если нет — берём значения из фильтров
        String yearToUse = (formEntry != null && formEntry.getAcademicYear() != null) ? formEntry.getAcademicYear() : currentFilterAcademicYear;
        Integer semesterToUse = (formEntry != null && formEntry.getSemester() != null) ? formEntry.getSemester() : currentFilterSemester;

        // Если ни в форме, ни в фильтрах ничего не указано — устанавливаем значение по умолчанию
        if (yearToUse == null || semesterToUse == null) {
            yearToUse = "2024-2025";
            semesterToUse = 2;
        }

        // Строим ключ для поиска даты начала семестра в статическом маппинге SEMESTER_START_DATES
        String semesterDisplayKey = yearToUse + "_" + semesterToUse;
        LocalDate semesterStartDateValue = ScheduleService.SEMESTER_START_DATES.get(semesterDisplayKey);

        // Если дата начала семестра найдена
        if (semesterStartDateValue != null) {
            // Форматируем её как строку dd.MM.yyyy
            displaySemesterStartDate = semesterStartDateValue.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            // Для второго семестра 2024–2025 года задаём конкретную дату окончания
            if (yearToUse.equals("2024-2025") && semesterToUse == 2) {
                displaySemesterEndDate = LocalDate.of(2025,5,17)
                        .format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            } else {
                // Для других случаев считаем, что семестр длится 14 недель и заканчивается в воскресенье
                displaySemesterEndDate = semesterStartDateValue.plusWeeks(14)
                        .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                        .format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            }
        }
        // Передаём начало и конец семестра в модель для отображения на странице
        model.addAttribute("semesterStartDate", displaySemesterStartDate);
        model.addAttribute("semesterEndDate", displaySemesterEndDate);

        // Проверка объекта formEntry содержит ли все нужные данные — год, семестр и номер недели
        if (formEntry != null && formEntry.getAcademicYear() != null && formEntry.getSemester() != null && formEntry.getWeekNumber() != null) {
            model.addAttribute("cancelFormYear", formEntry.getAcademicYear());
            model.addAttribute("cancelFormSemester", formEntry.getSemester());
            model.addAttribute("cancelFormWeek", formEntry.getWeekNumber());
            model.addAttribute("cancelFormGroupName", formEntry.getGroupName());
        } else {
            // Если данных нет или они неполные, то используем значения по умолчанию
            model.addAttribute("cancelFormYear", currentFilterAcademicYear);
            model.addAttribute("cancelFormSemester", currentFilterSemester);
            model.addAttribute("cancelFormWeek", currentFilterWeek);
            model.addAttribute("cancelFormGroupName", isAdmin ? null : currentUserGroupName);
        }

        // получаем данные из формы, но проверяем, что сама форма (formEntry) вообще существует. Если нет — ставим значение null
        String formEntryAcademicYear = (formEntry != null) ? formEntry.getAcademicYear() : null;
        Integer formEntrySemester = (formEntry != null) ? formEntry.getSemester() : null;
        Integer formEntryWeekNumber = (formEntry != null) ? formEntry.getWeekNumber() : null;

        // Проверяем: пользователь указал все три параметра
        if (formEntryAcademicYear != null && formEntrySemester != null && formEntryWeekNumber != null) {
            try {
                // Дата начала недели через calculateStartDateForAcademicWeek
                LocalDate startOfSelectedWeek = scheduleService.calculateStartDateForAcademicWeek(formEntryAcademicYear, formEntrySemester, formEntryWeekNumber);
                // Конец недели = начало + 6 дней, напр. 09.09 → 15.09
                LocalDate endOfSelectedWeek = startOfSelectedWeek.plusDays(6);
                // Задаём шаблон, по которому будем форматировать дату и время
                String dateTimeFormatPattern = "yyyy-MM-dd'T'HH:mm";
                // Создаём строку с минимально возможным временем в начале недели (в 00:00)
                model.addAttribute("minDateTimeForWeek", startOfSelectedWeek.atStartOfDay().format(DateTimeFormatter.ofPattern(dateTimeFormatPattern)));
                // Создаём строку с максимальным временем в конце недели (в 23:59)
                model.addAttribute("maxDateTimeForWeek", endOfSelectedWeek.atTime(23, 59).format(DateTimeFormatter.ofPattern(dateTimeFormatPattern)));
                // Создаём строку начала недели в 8 часов утра
                model.addAttribute("defaultStartDateTimeForWeek", startOfSelectedWeek.atTime(8,0).format(DateTimeFormatter.ofPattern(dateTimeFormatPattern)));
            } catch (IllegalArgumentException e) {
                log.warn("Не удалось рассчитать границы дат для формы (populate): {}", e.getMessage());
                model.addAttribute("minDateTimeForWeek", "");
                model.addAttribute("maxDateTimeForWeek", "");
                model.addAttribute("defaultStartDateTimeForWeek", "");
            } catch (Exception e) {
                log.error("Неожиданная ошибка при вычислении границ дат для schedule-form (populate): {}", e.getMessage(), e);
            }
        }
    }

    @GetMapping
    // Метод showSchedule возвращает имя шаблона
    public String showSchedule(
            // параметры приходят из URL как query-параметры
            @RequestParam(value = "academicYear", required = false) String filterAcademicYear, // Получаем из запроса значение параметра с названием academicYear
            @RequestParam(value = "semester", required = false) Integer filterSemester, // Получаем номер семестра из запроса (1 или 2)
            @RequestParam(value = "week", required = false) Integer filterWeekNumber, // Получаем номер недели из запроса
            @RequestParam(value = "groupName", required = false) String filterGroupNameFromRequest, // Получаем имя группы
            // Получаем дату из запроса в формате yyyy-MM-dd
            @RequestParam(value = "calendarWeek", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate requestedCalendarDate,
            Model model, Authentication authentication) { // Объект с данными текущего пользователя: статус входа, права, имя и т.п.

        // Пишем лог, чтобы понимать, какие фильтры были переданы в запросе
        log.info(">>> Запрос на расписание. Фильтры: Год [{}], Сем [{}], Неделя [{}], Группа из запроса [{}]. Календ.Неделя [{}]",
                filterAcademicYear, filterSemester, filterWeekNumber, filterGroupNameFromRequest, requestedCalendarDate);

        // Получаем имя текущего пользователя
        String currentPrincipalName = authentication.getName();
        // Проверяем есть ли у него роль Админ
        // Если да — isAdmin = true, иначе false.
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_ADMIN"));

        // пустая переменная для группы текущего пользователя
        String userGroupName = null;
        // Ищем пользователя по имени, возвращаем User или null
        User currentUser = userRepository.findByUsername(currentPrincipalName).orElse(null);
        // Если пользователь найден и он не админ , то сохраняем его группу в userGroupName
        if (currentUser != null && !isAdmin) {
            userGroupName = currentUser.getGroupName();
        } else if (currentUser != null && isAdmin) {
            userGroupName = currentUser.getGroupName();
        }


        // Создаём переменную для имени группы фильтра расписания
        String groupNameToFilterBy;
        // Если админ: берём группу из запроса или null, сохраняем в модель для отображения
        if (isAdmin) {
            if (filterGroupNameFromRequest != null && !filterGroupNameFromRequest.isBlank()) {
                groupNameToFilterBy = filterGroupNameFromRequest;
                model.addAttribute("selectedGroupName", filterGroupNameFromRequest);
            } else {
                groupNameToFilterBy = null;
            }
        } else { // Если не админ, показываем расписание только своей группы
            groupNameToFilterBy = userGroupName;
        }

        // Заполняем модель общими данными — год, семестр, неделя, группа и т.п. для отображения в форме
        populateCommonModelAttributes(model, filterAcademicYear, filterSemester, filterWeekNumber, null, userGroupName, isAdmin);
        addActivePageAttributes(model);

        List<ScheduleEntryDto> entries; // список записей расписания для показа
        LocalDate displayWeekStart; // дата начала недели для отображения расписания

        // Проверяем, были ли применены фильтры
        boolean academicFilterApplied = filterAcademicYear != null && !filterAcademicYear.isEmpty() &&
                filterSemester != null && filterWeekNumber != null;

        try {
            if (academicFilterApplied) {
                // Логируем использование фильтра по году, семестру и неделе
                log.info("--- Применена АКАДЕМИЧЕСКАЯ фильтрация: Год={}, Сем={}, Неделя={}, Группа для фильтра={}", filterAcademicYear, filterSemester, filterWeekNumber, groupNameToFilterBy);
                // Получаем расписание по году, семестру и неделе из сервиса
                entries = scheduleService.getEntriesByAcademicCriteria(filterAcademicYear, filterSemester, filterWeekNumber, groupNameToFilterBy, isAdmin);
                // Находим дату начала этой учебной недели
                displayWeekStart = scheduleService.calculateStartDateForAcademicWeek(filterAcademicYear, filterSemester, filterWeekNumber);
                // Добавляем параметры в модель для отображения на странице
                model.addAttribute("selectedAcademicYear", filterAcademicYear);
                model.addAttribute("selectedSemester", filterSemester);
                model.addAttribute("selectedWeekNumber", filterWeekNumber);
            } else if (requestedCalendarDate != null) {
                // Если указана конкретная дата через календарь, то:
                log.info("--- Применена КАЛЕНДАРНАЯ фильтрация: Дата={}, Группа для фильтра={}", requestedCalendarDate, groupNameToFilterBy);
                // Находим понедельник недели, куда попадает указанная дата
                displayWeekStart = requestedCalendarDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                // Получаем расписание на эту неделю
                entries = scheduleService.getEntriesForWeek(displayWeekStart, groupNameToFilterBy, isAdmin);
            } else {
                // Если ничего не выбрано, показываем расписание текущей недели
                log.info("--- Фильтры не применены. Используется текущая календарная неделя. Группа для фильтра={}", groupNameToFilterBy);
                // Находим понедельник текущей недели
                displayWeekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                // Получаем расписание на эту неделю
                entries = scheduleService.getEntriesForWeek(displayWeekStart, groupNameToFilterBy, isAdmin);
            }

            // При ошибке (например, неверный номер недели или даты)
            // логируем, передаём сообщение, показываем пустое расписание, используем текущую неделю
        } catch (IllegalArgumentException e) {
            log.error("Ошибка конфигурации дат семестров или неверные параметры фильтра: {}", e.getMessage());
            model.addAttribute("configErrorMessage", "Ошибка: " + e.getMessage() + ". Проверьте настройки дат семестров или параметры фильтра.");
            entries = Collections.emptyList();
            displayWeekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }
        // Добавляем в модель дату начала недели для отображения на странице
        model.addAttribute("currentWeekStart", displayWeekStart);

        // Создаём список 7 дат недели (пн–вс) и передаём в модель
        List<LocalDate> weekDates = new ArrayList<>();
        for (int i = 0; i < 7; i++) { weekDates.add(displayWeekStart.plusDays(i)); }
        model.addAttribute("weekDates", weekDates);

        // Создаём список из 24 времён (00:00–23:00) для часового расписания на странице
        List<LocalTime> timeSlots = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            timeSlots.add(LocalTime.of(hour, 0));
        }
        model.addAttribute("timeSlots", timeSlots);

        // Создаём структуру:
        // дата (LocalDate) → время (LocalTime, по часу) → список записей на это время
        Map<LocalDate, Map<LocalTime, List<ScheduleEntryDto>>> scheduleMap = new HashMap<>();
        // Если есть entries: фильтруем по наличию времени начала, группируем по дате и часу (без минут, секунд и наносекунд)
        if (entries != null) {
            scheduleMap = entries.stream()
                    .filter(e -> e.getStartTime() != null)
                    .collect(Collectors.groupingBy(
                            e -> e.getStartTime().toLocalDate(),
                            Collectors.groupingBy(
                                    // Группируем по часу начала записи
                                    e -> e.getStartTime().toLocalTime().withMinute(0).withSecond(0).withNano(0),
                                    Collectors.toList()
                            )
                    ));
        }
        // Передаём эту структуру в модель, чтобы использовать её на странице
        model.addAttribute("scheduleMap", scheduleMap);
        // Указываем русскую локализацию для форматирования дат и дней недели
        model.addAttribute("locale", new Locale("ru"));
        // Передаём весь список записей расписания — для отображения или поиска
        model.addAttribute("entries", entries);

        log.info("<<< Отображение шаблона 'schedule-weekly'");
        return "schedule-weekly";
    }

    // Метод вызывается при открытии страницы /schedule/new
    @GetMapping("/new")
    public String showCreateForm(Model model, Authentication authentication,
                                 @RequestParam(value = "activeYear", required = false) String activeYear,
                                 @RequestParam(value = "activeSemester", required = false) Integer activeSemester,
                                 @RequestParam(value = "activeWeek", required = false) Integer activeWeek) {
        log.debug("Запрос формы для создания новой записи (GET /schedule/new) с активными фильтрами Y:{}, S:{}, W:{}", activeYear, activeSemester, activeWeek);

        // Создаём новый ScheduleEntryDto — DTO для формы добавления записи в расписание
        ScheduleEntryDto newEntry = new ScheduleEntryDto();
        // Проверяем, есть ли у пользователя роль Админ
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN"));

        String currentAdminGroupName = null;
        // Ищем в базе данных текущего пользователя по имени
        User currentUser = userRepository.findByUsername(authentication.getName()).orElse(null);
        if (currentUser != null) {
            currentAdminGroupName = currentUser.getGroupName();
        }

        // Применяем активные фильтры к форме
        // Если есть activeYear, activeSemester, activeWeek — устанавливаем их в новую запись для формы
        if (activeYear != null) newEntry.setAcademicYear(activeYear);
        if (activeSemester != null) newEntry.setSemester(activeSemester);
        if (activeWeek != null) newEntry.setWeekNumber(activeWeek);

        // Добавляем новую запись в модель для отображения формы с предзаполненными полями
        model.addAttribute("entry", newEntry);
        // Указываем заголовок страницы для отображения в шаблоне
        model.addAttribute("pageTitle", "Добавить новую запись в расписание");
        // Вызываем метод, добавляющий в модель общие данные: учебные годы, семестры, недели и т.д.
        populateCommonModelAttributes(model, activeYear, activeSemester, activeWeek, newEntry, currentAdminGroupName, isAdmin);
        addActivePageAttributes(model);
        return "schedule-form";
    }

    @GetMapping("/edit/{id}")
    // Метод showEditForm обрабатывает редактирование по ID, передаёт данные в шаблон, работает с перенаправлениями и аутентификацией
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes, Authentication authentication) {
        log.debug("Запрос формы для редактирования записи расписания ID: {}", id);
        // Проверяем, является ли пользователь админом
        boolean isAdmin = authentication.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN"));

        String currentAdminGroupName = null;
        // Если пользователь не админ, получаем его группу для проверки доступа к редактированию
        User currentUser = userRepository.findByUsername(authentication.getName()).orElse(null);
        if (currentUser != null) {
            currentAdminGroupName = currentUser.getGroupName();
        }
        try {
            // Получаем запись расписания по её ID из сервиса
            ScheduleEntryDto entryDto = scheduleService.getEntryById(id);

            // Передаём entryDto в модель для отображения текущих значений и устанавливаем заголовок страницы
            model.addAttribute("entry", entryDto);
            model.addAttribute("pageTitle", "Редактировать запись расписания (ID: " + id + ")");
            // Добавляем в модель данные для выпадающих списков: годы, семестры, недели и другие
            populateCommonModelAttributes(model, entryDto.getAcademicYear(), entryDto.getSemester(), entryDto.getWeekNumber(), entryDto, currentAdminGroupName, isAdmin);
            addActivePageAttributes(model);
            return "schedule-form";
        } catch (EntityNotFoundException e) {
            // Если запись не найдена — пишем в лог, передаём ошибку и перенаправляем обратно
            log.warn("Запись расписания с ID {} не найдена для редактирования.", id);
            redirectAttributes.addFlashAttribute("errorMessage", "Запись с ID " + id + " не найдена.");
            return "redirect:/schedule";
        }
    }

    // Вспомогательный метод для безопасного кодирования строки в URL-параметр
    private String encodeURLParam(String param) {
        // Если строка пустая или null, возвращаем пустую строку — кодировать нечего
        if (param == null || param.isBlank()) return "";
        try {
            // Кодируем строку в UTF-8 для использования в URL
            return URLEncoder.encode(param, StandardCharsets.UTF_8.toString());
            // Если что-то пошло не так — пишем в лог и возвращаем исходную строку без кодирования
        } catch (UnsupportedEncodingException e) {
            log.warn("Не удалось URL-кодировать параметр: {}", param, e);
            return param;
        }
    }

    // Метод строит URL для перенаправления, например, после сохранения формы
    private String buildRedirectUrl(ScheduleEntryDto entryDto, boolean isAdmin, String currentFilterGroupName) {
        // Начинаем собирать URL: /schedule?academicYear=...
        StringBuilder urlBuilder = new StringBuilder("/schedule?academicYear=");
        // Добавляем учебный год из объекта entryDto и сразу кодируем его
        urlBuilder.append(encodeURLParam(entryDto.getAcademicYear()));
        // Добавляем номер семестра и номер недели как параметры запроса
        urlBuilder.append("&semester=").append(entryDto.getSemester());
        urlBuilder.append("&week=").append(entryDto.getWeekNumber());

        if (isAdmin) {
            // Выбираем группу для фильтра: сначала текущий фильтр, иначе — из записи (entryDto)
            String groupNameToRedirect = (currentFilterGroupName != null && !currentFilterGroupName.isBlank()) ?
                    currentFilterGroupName : entryDto.getGroupName();
            // Если группа найдена, добавляем её в URL, закодировав
            if (groupNameToRedirect != null && !groupNameToRedirect.isBlank()) {
                urlBuilder.append("&groupName=").append(encodeURLParam(groupNameToRedirect));
            }
        }
        // Возвращаем готовый URL как строку
        return urlBuilder.toString();
    }


    @PostMapping("/save")
    // данные из формы, которые проходят валидацию
    public String saveEntry(@Valid @ModelAttribute("entry") ScheduleEntryDto entryDto,
                            // содержит ошибки валидации (если они есть)
                            BindingResult bindingResult,
                            // чтобы передать сообщения при перенаправлении
                            RedirectAttributes redirectAttributes,
                            // для добавления данных на страницу и  информация о текущем пользователе
                            Model model, Authentication authentication,
                            // группа, выбранная в фильтре на главной странице
                            @RequestParam(value = "groupName", required = false) String filterGroupNameFromPage) {
        log.info("Попытка сохранения новой записи расписания: {}", entryDto);
        // Проверяем, админ ли пользователь
        boolean isAdmin = authentication.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN"));

        String currentAdminGroupName = null;
        // Получаем объект текущего пользователя по его имени
        User currentUser = userRepository.findByUsername(authentication.getName()).orElse(null);
        if (currentUser != null) {
            currentAdminGroupName = currentUser.getGroupName();
        }
        // Если пользователь — админ и не указал группу — добавляем ошибку в bindingResult для валидации
        if (isAdmin && (entryDto.getGroupName() == null || entryDto.getGroupName().isBlank())) {
            bindingResult.rejectValue("groupName", "error.entry", "Администратор должен указать группу для записи.");
        }
        // Проверяем, есть ли ошибки ввода
        if (bindingResult.hasErrors()) {
            log.warn("Ошибки валидации при сохранении записи расписания: {}", bindingResult.getAllErrors());
            model.addAttribute("pageTitle", "Добавить новую запись в расписание (Ошибка!)");
            // Перезаполняем модель данными, чтобы форма отобразилась с введёнными значениями и списками
            populateCommonModelAttributes(model, entryDto.getAcademicYear(), entryDto.getSemester(), entryDto.getWeekNumber(), entryDto, currentAdminGroupName, isAdmin);
            addActivePageAttributes(model);
            return "schedule-form";
        }
        try {
            // Вызываем сервис для сохранения записи в расписании
            scheduleService.createEntry(entryDto);
            redirectAttributes.addFlashAttribute("successMessage", "Запись успешно добавлена!");
            log.info("Новая запись расписания успешно сохранена.");
            // Перенаправляем на страницу расписания с фильтрами через buildRedirectUrl
            return "redirect:" + buildRedirectUrl(entryDto, isAdmin, filterGroupNameFromPage);
        } catch (Exception e) {
            log.error("Ошибка при сохранении записи расписания: {}", e.getMessage(), e);
            // Передаём краткое описание ошибки на страницу
            model.addAttribute("errorMessage", "Ошибка при сохранении записи: " + e.getMessage());
            // Устанавливаем заголовок страницы как ошибочный
            model.addAttribute("pageTitle", "Добавить новую запись в расписание (Ошибка!)");
            // Снова заполняем модель, чтобы форма снова отобразилась корректно
            populateCommonModelAttributes(model, entryDto.getAcademicYear(), entryDto.getSemester(), entryDto.getWeekNumber(), entryDto, currentAdminGroupName, isAdmin);
            addActivePageAttributes(model);
            return "schedule-form";
        }
    }

    @PostMapping("/update")
    // Получаем заполненную форму (ScheduleEntryDto) и проверяем на корректность с помощью @Valid
    public String updateEntry(@Valid @ModelAttribute("entry") ScheduleEntryDto entryDto,
                              // Содержит результаты валидации: ошибки полей, если они есть
                              BindingResult bindingResult,
                              // Используется для передачи сообщений при перенаправлении
                              RedirectAttributes redirectAttributes,
                              // для передачи данных в шаблон и информация о пользователе и его правах
                              Model model, Authentication authentication,
                              // Получаем имя группы из фильтра главной страницы, чтобы после обновления вернуть пользователя обратно
                              @RequestParam(value = "groupName", required = false) String filterGroupNameFromPage) { // Группа, которая была в фильтре на главной
        log.info("Попытка обновления записи расписания ID {}: {}", entryDto.getId(), entryDto);
        // Проверяем, админ ли пользователь
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN"));

        String currentAdminGroupName = null;
        // Если пользователь найден — сохраняем его группу для дальнейших проверок
        User currentUser = userRepository.findByUsername(authentication.getName()).orElse(null);
        if (currentUser != null) {
            currentAdminGroupName = currentUser.getGroupName();
        }

        // Если ID отсутствует — логируем ошибку, отправляем сообщение и перенаправляем обратно на расписание
        if (entryDto.getId() == null) {
            log.error("Попытка обновления записи расписания без ID!");
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка: ID записи не указан для обновления.");
            return "redirect:/schedule";
        }

        // Если админ не указал группу — добавляем ошибку валидации, форма не пройдёт, поле нужно исправить
        if (isAdmin && (entryDto.getGroupName() == null || entryDto.getGroupName().isBlank())) {
            bindingResult.rejectValue("groupName", "error.entry", "Администратор должен указать группу для записи.");
        }

        if (bindingResult.hasErrors()) {
            log.warn("Ошибки валидации при обновлении записи расписания ID {}: {}", entryDto.getId(), bindingResult.getAllErrors());
            // Меняем заголовок страницы, чтобы пользователь сразу увидел — были ошибки
            model.addAttribute("pageTitle", "Редактировать запись расписания (ID: " + entryDto.getId() + ") (Ошибка!)");
            // Перезаполняем модель данными, чтобы форма отобразилась с введёнными значениями и выпадающими списками
            populateCommonModelAttributes(model, entryDto.getAcademicYear(), entryDto.getSemester(), entryDto.getWeekNumber(), entryDto, currentAdminGroupName, isAdmin);
            addActivePageAttributes(model);
            return "schedule-form";
        }
        try {
            // Вызываем сервис для обновления записи в базе данных по её ID
            scheduleService.updateEntry(entryDto.getId(), entryDto);
            redirectAttributes.addFlashAttribute("successMessage", "Запись успешно обновлена!");
            log.info("Запись расписания ID {} успешно обновлена.", entryDto.getId());
            // Перенаправляем на страницу расписания с фильтрами через метод `buildRedirectUrl`
            return "redirect:" + buildRedirectUrl(entryDto, isAdmin, filterGroupNameFromPage);

        } catch (EntityNotFoundException e) { // Ловим исключение, если запись с таким ID не найдена
            log.warn("Запись расписания с ID {} не найдена для обновления.", entryDto.getId());
            redirectAttributes.addFlashAttribute("errorMessage", "Запись с ID " + entryDto.getId() + " не найдена.");
            return "redirect:/schedule";

        } catch (Exception e) { // Ловим любые другие непредвиденные ошибки
            log.error("Ошибка при обновлении записи расписания ID {}: {}", entryDto.getId(), e.getMessage(), e);
            // Передаём краткое описание ошибки на страницу
            model.addAttribute("errorMessage", "Ошибка при обновлении: " + e.getMessage());
            // Устанавливаем заголовок страницы как ошибочный
            model.addAttribute("pageTitle", "Редактировать запись расписания (ID: " + entryDto.getId() + ") (Ошибка!)");
            // Снова заполняем модель, чтобы форма снова отобразилась корректно
            populateCommonModelAttributes(model, entryDto.getAcademicYear(), entryDto.getSemester(), entryDto.getWeekNumber(), entryDto, currentAdminGroupName, isAdmin);
            addActivePageAttributes(model);
            return "schedule-form";
        }
    }

    @GetMapping("/delete/{id}")
    public String deleteEntry(
            @PathVariable Long id, // ID удаляемой записи
            RedirectAttributes redirectAttributes, // для сообщений после перенаправления
            Authentication authentication, // информация о пользователе
            @RequestParam(value = "groupName", required = false) String filterGroupNameFromPage) {
            // группа из фильтра для возврата после удаления

        log.info("Запрос на удаление записи расписания ID: {}", id);
        String targetRedirectUrl = "/schedule";
        // Проверяем, является ли пользователь администратором
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN"));
        ScheduleEntryDto entryDto = null; // Создаём переменную для данных удаляемой записи

        try { // Получаем запись по ID из сервиса
            entryDto = scheduleService.getEntryById(id);
            scheduleService.deleteEntry(id); // Вызываем метод удаления записи
            redirectAttributes.addFlashAttribute("successMessage", "Запись ID " + id + " успешно удалена.");
            log.info("Запись расписания ID {} успешно удалена.", id);
            targetRedirectUrl = buildRedirectUrl(entryDto, isAdmin, filterGroupNameFromPage);

            //  Обработка случая, если запись не найдена
        } catch (EntityNotFoundException e) {
            log.warn("Запись расписания с ID {} не найдена для удаления.", id);
            redirectAttributes.addFlashAttribute("errorMessage", "Запись с ID " + id + " не найдена для удаления.");
            // Обработка других ошибок
        } catch (Exception e) {
            log.error("Ошибка при удалении записи расписания ID {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка при удалении записи ID " + id + ": " + e.getMessage());
        }
        return "redirect:" + targetRedirectUrl;
    }

    // Метод обрабатывает GET-запрос по `/api/week-boundaries` и возвращает данные в формате JSON благодаря `@ResponseBody`
    @GetMapping("/api/week-boundaries")
    @ResponseBody
    // Принимаем параметры из URL:
    public ResponseEntity<Map<String, String>> getWeekBoundaries(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) Integer semester,
            @RequestParam(required = false) Integer week) {

        // Если отсутствует хотя бы один обязательный параметр — логируем и возвращаем пустой объект
        if (academicYear == null || academicYear.isEmpty() || semester == null || week == null) {
            log.warn("API /week-boundaries: Не все параметры предоставлены (Год: {}, Сем: {}, Неделя: {}).", academicYear, semester, week);
            return ResponseEntity.ok(Collections.emptyMap());
        }
        // Возвращаем карту (ключ-значение) для временных границ недели
        Map<String, String> boundaries = new HashMap<>();
        try {
            // Используем сервис для поиска даты начала указанной учебной недели
            LocalDate startOfSelectedWeek = scheduleService.calculateStartDateForAcademicWeek(academicYear, semester, week);
            // Конец недели — 6 дней после начала (всего 7 дней, включая стартовую дату)
            LocalDate endOfSelectedWeek = startOfSelectedWeek.plusDays(6);
            // Задаём формат даты и времени
            String dateTimeFormatPattern = "yyyy-MM-dd'T'HH:mm";
            // Минимальное время недели (например, 2024-09-09T00:00)
            boundaries.put("minDateTime", startOfSelectedWeek.atStartOfDay().format(DateTimeFormatter.ofPattern(dateTimeFormatPattern)));
            // Максимальное время недели (например, 2024-09-15T23:59)
            boundaries.put("maxDateTime", endOfSelectedWeek.atTime(23, 59).format(DateTimeFormatter.ofPattern(dateTimeFormatPattern)));
            // Время по умолчанию (например, 2024-09-09T08:00)
            boundaries.put("defaultStartTime", startOfSelectedWeek.atTime(8, 0).format(DateTimeFormatter.ofPattern(dateTimeFormatPattern)));
            // Возвращаем клиенту JSON с временными границами
            return ResponseEntity.ok(boundaries);
        } catch (IllegalArgumentException e) {
            // Если данные неверны (например, неделя вне диапазона) — логируем и возвращаем JSON с сообщением об ошибке
            log.warn("API /week-boundaries: Ошибка конфигурации дат для {}/{}/{}: {}", academicYear, semester, week, e.getMessage());
            return ResponseEntity.ok(Collections.singletonMap("error", e.getMessage()));
        } catch (Exception e) {
            // При любой другой ошибке — логируем полностью и возвращаем статус 500 с сообщением об ошибке
            log.error("Ошибка при получении границ недели через API для {}/{}/{}: {}", academicYear, semester, week, e.getMessage());
            return ResponseEntity.status(500).body(Collections.singletonMap("error", "Не удалось рассчитать границы недели: " + e.getMessage()));
        }
    }
}