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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils; // Для URL кодирования

import java.io.UnsupportedEncodingException; // Для обработки исключения URLEncoder
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

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

    private void populateCommonModelAttributes(Model model,
                                               String currentFilterAcademicYear,
                                               Integer currentFilterSemester,
                                               Integer currentFilterWeek,
                                               ScheduleEntryDto formEntry,
                                               String currentUserGroupName,
                                               boolean isAdmin) {
        model.addAttribute("academicYears", Arrays.asList("2024-2025", "2025-2026", "2026-2027"));
        model.addAttribute("semesters", Arrays.asList(1, 2));
        List<Integer> weeks = new ArrayList<>();
        for (int i = 1; i <= 18; i++) weeks.add(i);
        model.addAttribute("weekNumbers", weeks);
        model.addAttribute("availableGroups", List.of("ВТ-23А", "ВТ-23Б", "ПИ-22А", "ИС-21А"));
        model.addAttribute("currentUserGroupName", currentUserGroupName);
        model.addAttribute("isAdmin", isAdmin);

        String displaySemesterStartDate = "N/A";
        String displaySemesterEndDate = "N/A";
        String yearToUse = (formEntry != null && formEntry.getAcademicYear() != null) ? formEntry.getAcademicYear() : currentFilterAcademicYear;
        Integer semesterToUse = (formEntry != null && formEntry.getSemester() != null) ? formEntry.getSemester() : currentFilterSemester;

        if (yearToUse == null || semesterToUse == null) {
            yearToUse = "2024-2025";
            semesterToUse = 2;
        }
        String semesterDisplayKey = yearToUse + "_" + semesterToUse;
        LocalDate semesterStartDateValue = ScheduleService.SEMESTER_START_DATES.get(semesterDisplayKey);

        if (semesterStartDateValue != null) {
            displaySemesterStartDate = semesterStartDateValue.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            if (yearToUse.equals("2024-2025") && semesterToUse == 2) {
                displaySemesterEndDate = LocalDate.of(2025,5,17).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            } else {
                displaySemesterEndDate = semesterStartDateValue.plusWeeks(14).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            }
        }
        model.addAttribute("semesterStartDate", displaySemesterStartDate);
        model.addAttribute("semesterEndDate", displaySemesterEndDate);

        if (formEntry != null && formEntry.getAcademicYear() != null && formEntry.getSemester() != null && formEntry.getWeekNumber() != null) {
            model.addAttribute("cancelFormYear", formEntry.getAcademicYear());
            model.addAttribute("cancelFormSemester", formEntry.getSemester());
            model.addAttribute("cancelFormWeek", formEntry.getWeekNumber());
        } else {
            model.addAttribute("cancelFormYear", currentFilterAcademicYear);
            model.addAttribute("cancelFormSemester", currentFilterSemester);
            model.addAttribute("cancelFormWeek", currentFilterWeek);
        }

        String formEntryAcademicYear = (formEntry != null) ? formEntry.getAcademicYear() : null;
        Integer formEntrySemester = (formEntry != null) ? formEntry.getSemester() : null;
        Integer formEntryWeekNumber = (formEntry != null) ? formEntry.getWeekNumber() : null;

        if (formEntryAcademicYear != null && formEntrySemester != null && formEntryWeekNumber != null) {
            try {
                LocalDate startOfSelectedWeek = scheduleService.calculateStartDateForAcademicWeek(formEntryAcademicYear, formEntrySemester, formEntryWeekNumber);
                LocalDate endOfSelectedWeek = startOfSelectedWeek.plusDays(6);
                String dateTimeFormatPattern = "yyyy-MM-dd'T'HH:mm";
                model.addAttribute("minDateTimeForWeek", startOfSelectedWeek.atStartOfDay().format(DateTimeFormatter.ofPattern(dateTimeFormatPattern)));
                model.addAttribute("maxDateTimeForWeek", endOfSelectedWeek.atTime(23, 59).format(DateTimeFormatter.ofPattern(dateTimeFormatPattern)));
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
    public String showSchedule(
            @RequestParam(value = "academicYear", required = false) String filterAcademicYear,
            @RequestParam(value = "semester", required = false) Integer filterSemester,
            @RequestParam(value = "week", required = false) Integer filterWeekNumber,
            @RequestParam(value = "groupName", required = false) String filterGroupName,
            @RequestParam(value = "calendarWeek", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate requestedCalendarDate,
            Model model, Authentication authentication) {

        log.info(">>> Запрос на расписание. Фильтры: Год [{}], Сем [{}], Неделя [{}], Группа [{}]. Календ.Неделя [{}]",
                filterAcademicYear, filterSemester, filterWeekNumber, filterGroupName, requestedCalendarDate);

        String currentPrincipalName = authentication.getName();
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_ADMIN"));

        String userGroupName = null;
        User currentUser = userRepository.findByUsername(currentPrincipalName).orElse(null);
        if (currentUser != null && !isAdmin) {
            userGroupName = currentUser.getGroupName();
        }

        String groupNameToFilterBy = isAdmin ? filterGroupName : userGroupName;

        populateCommonModelAttributes(model, filterAcademicYear, filterSemester, filterWeekNumber, null, userGroupName, isAdmin);
        addActivePageAttributes(model);

        List<ScheduleEntryDto> entries;
        LocalDate displayWeekStart;

        boolean academicFilterApplied = filterAcademicYear != null && !filterAcademicYear.isEmpty() &&
                filterSemester != null && filterWeekNumber != null;

        try {
            if (academicFilterApplied) {
                log.info("--- Применена АКАДЕМИЧЕСКАЯ фильтрация: Год={}, Сем={}, Неделя={}, Группа для фильтра={}", filterAcademicYear, filterSemester, filterWeekNumber, groupNameToFilterBy);
                entries = scheduleService.getEntriesByAcademicCriteria(filterAcademicYear, filterSemester, filterWeekNumber, groupNameToFilterBy, isAdmin);
                displayWeekStart = scheduleService.calculateStartDateForAcademicWeek(filterAcademicYear, filterSemester, filterWeekNumber);
                model.addAttribute("selectedAcademicYear", filterAcademicYear);
                model.addAttribute("selectedSemester", filterSemester);
                model.addAttribute("selectedWeekNumber", filterWeekNumber);
                if (isAdmin && filterGroupName != null && !filterGroupName.isBlank()) {
                    model.addAttribute("selectedGroupName", filterGroupName);
                }
            } else if (requestedCalendarDate != null) {
                log.info("--- Применена КАЛЕНДАРНАЯ фильтрация: Дата={}, Группа для фильтра={}", requestedCalendarDate, groupNameToFilterBy);
                displayWeekStart = requestedCalendarDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                entries = scheduleService.getEntriesForWeek(displayWeekStart, groupNameToFilterBy, isAdmin);
            } else {
                log.info("--- Фильтры не применены. Используется текущая календарная неделя. Группа для фильтра={}", groupNameToFilterBy);
                displayWeekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                entries = scheduleService.getEntriesForWeek(displayWeekStart, groupNameToFilterBy, isAdmin);
            }
        } catch (IllegalArgumentException e) {
            log.error("Ошибка конфигурации дат семестров или неверные параметры фильтра: {}", e.getMessage());
            model.addAttribute("configErrorMessage", "Ошибка: " + e.getMessage() + ". Проверьте настройки дат семестров или параметры фильтра.");
            entries = Collections.emptyList();
            displayWeekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }

        model.addAttribute("currentWeekStart", displayWeekStart);

        List<LocalDate> weekDates = new ArrayList<>();
        for (int i = 0; i < 7; i++) { weekDates.add(displayWeekStart.plusDays(i)); }
        model.addAttribute("weekDates", weekDates);

        List<LocalTime> timeSlots = new ArrayList<>();
        for (int hour = 8; hour < 20; hour++) { timeSlots.add(LocalTime.of(hour, 0)); }
        model.addAttribute("timeSlots", timeSlots);

        Map<LocalDate, Map<LocalTime, List<ScheduleEntryDto>>> scheduleMap = new HashMap<>();
        if (entries != null) {
            scheduleMap = entries.stream()
                    .filter(e -> e.getStartTime() != null)
                    .collect(Collectors.groupingBy(
                            e -> e.getStartTime().toLocalDate(),
                            Collectors.groupingBy(
                                    e -> e.getStartTime().toLocalTime().withMinute(0).withSecond(0).withNano(0),
                                    Collectors.toList()
                            )
                    ));
        }
        model.addAttribute("scheduleMap", scheduleMap);
        model.addAttribute("locale", new Locale("ru"));
        model.addAttribute("entries", entries);

        log.info("<<< Отображение шаблона 'schedule-weekly'");
        return "schedule-weekly";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model, Authentication authentication,
                                 @RequestParam(value = "activeYear", required = false) String activeYear,
                                 @RequestParam(value = "activeSemester", required = false) Integer activeSemester,
                                 @RequestParam(value = "activeWeek", required = false) Integer activeWeek) {
        log.debug("Запрос формы для создания новой записи (GET /schedule/new) с активными фильтрами Y:{}, S:{}, W:{}", activeYear, activeSemester, activeWeek);
        ScheduleEntryDto newEntry = new ScheduleEntryDto();
        boolean isAdmin = authentication.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN"));
        String userGroupName = null;

        if (!isAdmin) {
            User currentUser = userRepository.findByUsername(authentication.getName()).orElse(null);
            if (currentUser != null) {
                userGroupName = currentUser.getGroupName();
                newEntry.setGroupName(userGroupName);
            }
        }

        if (activeYear != null) newEntry.setAcademicYear(activeYear);
        if (activeSemester != null) newEntry.setSemester(activeSemester);
        if (activeWeek != null) newEntry.setWeekNumber(activeWeek);

        model.addAttribute("entry", newEntry);
        model.addAttribute("pageTitle", "Добавить новую запись в расписание");
        populateCommonModelAttributes(model, activeYear, activeSemester, activeWeek, newEntry, userGroupName, isAdmin);
        addActivePageAttributes(model);
        return "schedule-form";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes, Authentication authentication) {
        log.debug("Запрос формы для редактирования записи расписания ID: {}", id);
        boolean isAdmin = authentication.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN"));
        String userGroupName = null;
        if (!isAdmin) {
            User currentUser = userRepository.findByUsername(authentication.getName()).orElse(null);
            if (currentUser != null) userGroupName = currentUser.getGroupName();
        }
        try {
            ScheduleEntryDto entryDto = scheduleService.getEntryById(id);
            if (!isAdmin && (entryDto.getGroupName() == null || !entryDto.getGroupName().equals(userGroupName))) {
                log.warn("Попытка студента {} редактировать запись (ID: {}) чужой группы.", authentication.getName(), id);
                redirectAttributes.addFlashAttribute("errorMessage", "Вы не можете редактировать записи для другой группы.");
                return "redirect:/schedule";
            }

            model.addAttribute("entry", entryDto);
            model.addAttribute("pageTitle", "Редактировать запись расписания (ID: " + id + ")");
            populateCommonModelAttributes(model, entryDto.getAcademicYear(), entryDto.getSemester(), entryDto.getWeekNumber(), entryDto, userGroupName, isAdmin);
            addActivePageAttributes(model);
            return "schedule-form";
        } catch (EntityNotFoundException e) {
            log.warn("Запись расписания с ID {} не найдена для редактирования.", id);
            redirectAttributes.addFlashAttribute("errorMessage", "Запись с ID " + id + " не найдена.");
            return "redirect:/schedule";
        }
    }

    private String buildRedirectUrl(ScheduleEntryDto entryDto, boolean isAdmin) {
        StringBuilder urlBuilder = new StringBuilder("/schedule?academicYear=");
        urlBuilder.append(encodeURLParam(entryDto.getAcademicYear()));
        urlBuilder.append("&semester=").append(entryDto.getSemester());
        urlBuilder.append("&week=").append(entryDto.getWeekNumber());
        if (isAdmin && entryDto.getGroupName() != null && !entryDto.getGroupName().isBlank()) {
            urlBuilder.append("&groupName=").append(encodeURLParam(entryDto.getGroupName()));
        }
        return urlBuilder.toString();
    }

    private String encodeURLParam(String param) {
        if (param == null) return "";
        try {
            return UriUtils.encode(param, StandardCharsets.UTF_8); // Используем UriUtils для более корректного кодирования
        } catch (Exception e) { // UnsupportedEncodingException больше не выбрасывается UriUtils.encode с UTF-8
            log.warn("Не удалось URL-кодировать параметр: {}", param, e);
            return param; // Возвращаем исходный в случае редкой ошибки
        }
    }


    @PostMapping("/save")
    public String saveEntry(@Valid @ModelAttribute("entry") ScheduleEntryDto entryDto,
                            BindingResult bindingResult,
                            RedirectAttributes redirectAttributes,
                            Model model, Authentication authentication) {
        log.info("Попытка сохранения новой записи расписания: {}", entryDto);
        boolean isAdmin = authentication.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN"));
        String userGroupName = null;
        User currentUser = userRepository.findByUsername(authentication.getName()).orElse(null);

        if (!isAdmin && currentUser != null) {
            userGroupName = currentUser.getGroupName();
            if (userGroupName != null && (entryDto.getGroupName() == null || entryDto.getGroupName().isBlank())) {
                entryDto.setGroupName(userGroupName);
            } else if (userGroupName != null && !userGroupName.equals(entryDto.getGroupName())) {
                entryDto.setGroupName(userGroupName);
            }
        }

        if (bindingResult.hasErrors()) {
            log.warn("Ошибки валидации при сохранении записи расписания: {}", bindingResult.getAllErrors());
            model.addAttribute("pageTitle", "Добавить новую запись в расписание (Ошибка!)");
            populateCommonModelAttributes(model, entryDto.getAcademicYear(), entryDto.getSemester(), entryDto.getWeekNumber(), entryDto, userGroupName, isAdmin);
            addActivePageAttributes(model);
            return "schedule-form";
        }
        try {
            scheduleService.createEntry(entryDto);
            redirectAttributes.addFlashAttribute("successMessage", "Запись успешно добавлена!");
            log.info("Новая запись расписания успешно сохранена.");
            return "redirect:" + buildRedirectUrl(entryDto, isAdmin);
        } catch (Exception e) {
            log.error("Ошибка при сохранении записи расписания: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "Ошибка при сохранении записи: " + e.getMessage());
            model.addAttribute("pageTitle", "Добавить новую запись в расписание (Ошибка!)");
            populateCommonModelAttributes(model, entryDto.getAcademicYear(), entryDto.getSemester(), entryDto.getWeekNumber(), entryDto, userGroupName, isAdmin);
            addActivePageAttributes(model);
            return "schedule-form";
        }
    }

    @PostMapping("/update")
    public String updateEntry(@Valid @ModelAttribute("entry") ScheduleEntryDto entryDto,
                              BindingResult bindingResult,
                              RedirectAttributes redirectAttributes,
                              Model model, Authentication authentication) {
        log.info("Попытка обновления записи расписания ID {}: {}", entryDto.getId(), entryDto);
        boolean isAdmin = authentication.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN"));
        String userGroupName = null; // Группа текущего пользователя
        User currentUser = userRepository.findByUsername(authentication.getName()).orElse(null);

        if (entryDto.getId() == null) {
            log.error("Попытка обновления записи расписания без ID!");
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка: ID записи не указан для обновления.");
            return "redirect:/schedule";
        }

        ScheduleEntryDto existingDtoFromDb = null;
        try {
            existingDtoFromDb = scheduleService.getEntryById(entryDto.getId());
        } catch (EntityNotFoundException e) {
            log.warn("Запись ID {} не найдена при попытке обновления", entryDto.getId());
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка: Запись для обновления не найдена.");
            return "redirect:/schedule";
        }

        if (!isAdmin && currentUser != null) {
            userGroupName = currentUser.getGroupName();
            if (existingDtoFromDb != null && (existingDtoFromDb.getGroupName() == null || !existingDtoFromDb.getGroupName().equals(userGroupName))) {
                log.warn("Попытка студента {} обновить запись (ID: {}) чужой группы.", authentication.getName(), entryDto.getId());
                redirectAttributes.addFlashAttribute("errorMessage", "Вы не можете изменять записи для другой группы.");
                return "redirect:/schedule";
            }
            if (userGroupName != null) {
                entryDto.setGroupName(userGroupName); // Студент не может сменить группу записи
            }
        }

        if (bindingResult.hasErrors()) {
            log.warn("Ошибки валидации при обновлении записи расписания ID {}: {}", entryDto.getId(), bindingResult.getAllErrors());
            model.addAttribute("pageTitle", "Редактировать запись расписания (ID: " + entryDto.getId() + ") (Ошибка!)");
            populateCommonModelAttributes(model, entryDto.getAcademicYear(), entryDto.getSemester(), entryDto.getWeekNumber(), entryDto, userGroupName, isAdmin);
            addActivePageAttributes(model);
            return "schedule-form";
        }
        try {
            scheduleService.updateEntry(entryDto.getId(), entryDto);
            redirectAttributes.addFlashAttribute("successMessage", "Запись успешно обновлена!");
            log.info("Запись расписания ID {} успешно обновлена.", entryDto.getId());
            return "redirect:" + buildRedirectUrl(entryDto, isAdmin);
        } catch (EntityNotFoundException e) { // Уже проверено выше, но на всякий случай
            log.warn("Запись расписания с ID {} не найдена для обновления.", entryDto.getId());
            redirectAttributes.addFlashAttribute("errorMessage", "Запись с ID " + entryDto.getId() + " не найдена.");
            return "redirect:/schedule";
        } catch (Exception e) {
            log.error("Ошибка при обновлении записи расписания ID {}: {}", entryDto.getId(), e.getMessage(), e);
            model.addAttribute("errorMessage", "Ошибка при обновлении: " + e.getMessage());
            model.addAttribute("pageTitle", "Редактировать запись расписания (ID: " + entryDto.getId() + ") (Ошибка!)");
            populateCommonModelAttributes(model, entryDto.getAcademicYear(), entryDto.getSemester(), entryDto.getWeekNumber(), entryDto, userGroupName, isAdmin);
            addActivePageAttributes(model);
            return "schedule-form";
        }
    }

    @GetMapping("/delete/{id}")
    public String deleteEntry(@PathVariable Long id, RedirectAttributes redirectAttributes, Authentication authentication) {
        log.info("Запрос на удаление записи расписания ID: {}", id);
        String targetRedirectUrl = "/schedule";
        boolean isAdmin = authentication.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN"));
        ScheduleEntryDto entryDto = null;

        try {
            entryDto = scheduleService.getEntryById(id);
            if (!isAdmin) {
                User currentUser = userRepository.findByUsername(authentication.getName()).orElse(null);
                if (currentUser == null || entryDto.getGroupName() == null || !entryDto.getGroupName().equals(currentUser.getGroupName())) {
                    log.warn("Попытка студента {} удалить запись (ID: {}) чужой группы.", authentication.getName(), id);
                    redirectAttributes.addFlashAttribute("errorMessage", "Вы не можете удалять записи для другой группы.");
                    return "redirect:/schedule";
                }
            }

            scheduleService.deleteEntry(id);
            redirectAttributes.addFlashAttribute("successMessage", "Запись ID " + id + " успешно удалена.");
            log.info("Запись расписания ID {} успешно удалена.", id);
            // Формируем URL для редиректа на основе данных удаленной записи
            if (entryDto != null) {
                targetRedirectUrl = buildRedirectUrl(entryDto, isAdmin);
            }
        } catch (EntityNotFoundException e) {
            log.warn("Запись расписания с ID {} не найдена для удаления.", id);
            redirectAttributes.addFlashAttribute("errorMessage", "Запись с ID " + id + " не найдена для удаления.");
        } catch (Exception e) {
            log.error("Ошибка при удалении записи расписания ID {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка при удалении записи ID " + id + ": " + e.getMessage());
        }
        return "redirect:" + targetRedirectUrl;
    }

    @GetMapping("/api/week-boundaries")
    @ResponseBody
    public ResponseEntity<Map<String, String>> getWeekBoundaries(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) Integer semester,
            @RequestParam(required = false) Integer week) {

        if (academicYear == null || academicYear.isEmpty() || semester == null || week == null) {
            log.warn("API /week-boundaries: Не все параметры предоставлены (Год: {}, Сем: {}, Неделя: {}).", academicYear, semester, week);
            return ResponseEntity.ok(Collections.emptyMap());
        }
        Map<String, String> boundaries = new HashMap<>();
        try {
            LocalDate startOfSelectedWeek = scheduleService.calculateStartDateForAcademicWeek(academicYear, semester, week);
            LocalDate endOfSelectedWeek = startOfSelectedWeek.plusDays(6);
            String dateTimeFormatPattern = "yyyy-MM-dd'T'HH:mm";
            boundaries.put("minDateTime", startOfSelectedWeek.atStartOfDay().format(DateTimeFormatter.ofPattern(dateTimeFormatPattern)));
            boundaries.put("maxDateTime", endOfSelectedWeek.atTime(23, 59).format(DateTimeFormatter.ofPattern(dateTimeFormatPattern)));
            boundaries.put("defaultStartTime", startOfSelectedWeek.atTime(8, 0).format(DateTimeFormatter.ofPattern(dateTimeFormatPattern)));
            return ResponseEntity.ok(boundaries);
        } catch (IllegalArgumentException e) {
            log.warn("API /week-boundaries: Ошибка конфигурации дат для {}/{}/{}: {}", academicYear, semester, week, e.getMessage());
            return ResponseEntity.ok(Collections.singletonMap("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Ошибка при получении границ недели через API для {}/{}/{}: {}", academicYear, semester, week, e.getMessage());
            return ResponseEntity.status(500).body(Collections.singletonMap("error", "Не удалось рассчитать границы недели: " + e.getMessage()));
        }
    }
}