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

    private void populateCommonModelAttributes(Model model, String selectedAcademicYear, Integer selectedSemester, Integer selectedWeekNumberForForm, String currentUserGroupName) {
        model.addAttribute("academicYears", Arrays.asList("2024-2025", "2025-2026"));
        model.addAttribute("semesters", Arrays.asList(1, 2));
        List<Integer> weeks = new ArrayList<>();
        for (int i = 1; i <= 18; i++) weeks.add(i);
        model.addAttribute("weekNumbers", weeks);
        model.addAttribute("availableGroups", List.of("ВТ-23А", "ВТ-23Б", "ПИ-22А"));
        model.addAttribute("currentUserGroupName", currentUserGroupName);

        String displaySemesterStartDate = "N/A";
        String displaySemesterEndDate = "N/A";
        String yearToUseForSemesterDisplay = selectedAcademicYear;
        Integer semesterToUseForSemesterDisplay = selectedSemester;

        if (yearToUseForSemesterDisplay == null || semesterToUseForSemesterDisplay == null) {
            yearToUseForSemesterDisplay = "2024-2025";
            semesterToUseForSemesterDisplay = 2;
        }

        String semesterDisplayKey = yearToUseForSemesterDisplay + "_" + semesterToUseForSemesterDisplay;
        LocalDate semesterStartDateValue = ScheduleService.SEMESTER_START_DATES.get(semesterDisplayKey);

        if (semesterStartDateValue != null) {
            displaySemesterStartDate = semesterStartDateValue.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            if (yearToUseForSemesterDisplay.equals("2024-2025") && semesterToUseForSemesterDisplay == 2) {
                displaySemesterEndDate = LocalDate.of(2025, 5, 17).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            } else {
                displaySemesterEndDate = semesterStartDateValue.plusWeeks(14).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            }
        }

        model.addAttribute("semesterStartDate", displaySemesterStartDate);
        model.addAttribute("semesterEndDate", displaySemesterEndDate);
        model.addAttribute("cancelFormYear", selectedAcademicYear);
        model.addAttribute("cancelFormSemester", selectedSemester);
        model.addAttribute("cancelFormWeek", selectedWeekNumberForForm);

        if (selectedAcademicYear != null && selectedSemester != null && selectedWeekNumberForForm != null) {
            try {
                LocalDate startOfSelectedWeek = scheduleService.calculateStartDateForAcademicWeek(selectedAcademicYear, selectedSemester, selectedWeekNumberForForm);
                LocalDate endOfSelectedWeek = startOfSelectedWeek.plusDays(6);
                String dateTimeFormatPattern = "yyyy-MM-dd'T'HH:mm";
                model.addAttribute("minDateTimeForWeek", startOfSelectedWeek.atStartOfDay().format(DateTimeFormatter.ofPattern(dateTimeFormatPattern)));
                model.addAttribute("maxDateTimeForWeek", endOfSelectedWeek.atTime(23, 59).format(DateTimeFormatter.ofPattern(dateTimeFormatPattern)));
                model.addAttribute("defaultStartDateTimeForWeek", startOfSelectedWeek.atTime(8, 0).format(DateTimeFormatter.ofPattern(dateTimeFormatPattern)));
            } catch (Exception e) {
                log.error("Ошибка при вычислении границ дат для schedule-form: {}", e.getMessage());
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
        populateCommonModelAttributes(model, filterAcademicYear, filterSemester, filterWeekNumber, userGroupName);
        addActivePageAttributes(model);

        List<ScheduleEntryDto> entries;
        LocalDate displayWeekStart;

        boolean academicFilterApplied = filterAcademicYear != null && !filterAcademicYear.isEmpty() &&
                filterSemester != null && filterWeekNumber != null;

        if (academicFilterApplied) {
            log.info("--- Применена АКАДЕМИЧЕСКАЯ фильтрация: Год={}, Сем={}, Неделя={}, Группа для фильтра={}", filterAcademicYear, filterSemester, filterWeekNumber, groupNameToFilterBy);
            entries = scheduleService.getEntriesByAcademicCriteria(filterAcademicYear, filterSemester, filterWeekNumber, groupNameToFilterBy, isAdmin);
            displayWeekStart = scheduleService.calculateStartDateForAcademicWeek(filterAcademicYear, filterSemester, filterWeekNumber);
            model.addAttribute("selectedAcademicYear", filterAcademicYear);
            model.addAttribute("selectedSemester", filterSemester);
            model.addAttribute("selectedWeekNumber", filterWeekNumber);
            if (isAdmin && filterGroupName != null) {
                model.addAttribute("selectedGroupName", filterGroupName);
            }
        } else if (requestedCalendarDate != null) {
            log.info("--- Применена КАЛЕНДАРНАЯ фильтрация: Дата={}, Группа для фильтра={}", requestedCalendarDate, groupNameToFilterBy);
            displayWeekStart = requestedCalendarDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            entries = scheduleService.getEntriesForWeek(displayWeekStart, groupNameToFilterBy, isAdmin);
        } else {
            log.info("--- Фильтры не применены. Группа для фильтра={}", groupNameToFilterBy);
            displayWeekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            entries = scheduleService.getEntriesForWeek(displayWeekStart, groupNameToFilterBy, isAdmin);
        }

        model.addAttribute("currentWeekStart", displayWeekStart);
        model.addAttribute("isAdmin", isAdmin);

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
    public String showCreateForm(Model model, Authentication authentication) {
        log.debug("Запрос формы для создания новой записи (GET /schedule/new)");
        ScheduleEntryDto newEntry = new ScheduleEntryDto();
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_ADMIN"));

        String userGroupName = null;
        if (!isAdmin) {
            User currentUser = userRepository.findByUsername(authentication.getName()).orElse(null);
            if (currentUser != null) {
                userGroupName = currentUser.getGroupName();
                newEntry.setGroupName(userGroupName);
            }
        }

        model.addAttribute("entry", newEntry);
        model.addAttribute("pageTitle", "Добавить новую запись в расписание");
        populateCommonModelAttributes(model, newEntry.getAcademicYear(), newEntry.getSemester(), newEntry.getWeekNumber(), userGroupName);
        addActivePageAttributes(model);
        model.addAttribute("isAdmin", isAdmin);
        return "schedule-form";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes, Authentication authentication) {
        log.debug("Запрос формы для редактирования записи расписания ID: {}", id);
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_ADMIN"));

        String userGroupName = null;
        if (!isAdmin) {
            User currentUser = userRepository.findByUsername(authentication.getName()).orElse(null);
            if (currentUser != null) userGroupName = currentUser.getGroupName();
        }

        try {
            ScheduleEntryDto entryDto = scheduleService.getEntryById(id);
            if (!isAdmin && (entryDto.getGroupName() == null || !entryDto.getGroupName().equals(userGroupName))) {
                log.warn("Попытка редактирования записи (ID: {}) чужой группы пользователем {}", id, authentication.getName());
                redirectAttributes.addFlashAttribute("errorMessage", "Вы не можете редактировать записи для другой группы.");
                return "redirect:/schedule";
            }

            model.addAttribute("entry", entryDto);
            model.addAttribute("pageTitle", "Редактировать запись расписания (ID: " + id + ")");
            populateCommonModelAttributes(model, entryDto.getAcademicYear(), entryDto.getSemester(), entryDto.getWeekNumber(), userGroupName);
            addActivePageAttributes(model);
            model.addAttribute("isAdmin", isAdmin);
            return "schedule-form";
        } catch (EntityNotFoundException e) {
            return "redirect:/schedule";
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
            if (userGroupName != null && (entryDto.getGroupName() == null || !entryDto.getGroupName().equals(userGroupName))) {
                log.info("Установка группы '{}' для новой записи студента {}", userGroupName, authentication.getName());
                entryDto.setGroupName(userGroupName);
            }
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("isAdmin", isAdmin);
            populateCommonModelAttributes(model, entryDto.getAcademicYear(), entryDto.getSemester(), entryDto.getWeekNumber(), userGroupName);
            addActivePageAttributes(model);
            return "schedule-form";
        }

        try {
            scheduleService.createEntry(entryDto);
        } catch (Exception e) {
            model.addAttribute("isAdmin", isAdmin);
            populateCommonModelAttributes(model, entryDto.getAcademicYear(), entryDto.getSemester(), entryDto.getWeekNumber(), userGroupName);
            addActivePageAttributes(model);
            return "schedule-form";
        }

        return "redirect:/schedule?academicYear=" + entryDto.getAcademicYear() +
                "&semester=" + entryDto.getSemester() +
                "&week=" + entryDto.getWeekNumber() +
                (isAdmin && entryDto.getGroupName() != null ? "&groupName=" + entryDto.getGroupName() : "");
    }

    @PostMapping("/update")
    public String updateEntry(@Valid @ModelAttribute("entry") ScheduleEntryDto entryDto,
                              BindingResult bindingResult,
                              RedirectAttributes redirectAttributes,
                              Model model, Authentication authentication) {
        log.info("Попытка обновления записи расписания ID {}: {}", entryDto.getId(), entryDto);
        boolean isAdmin = authentication.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN"));
        String userGroupName = null;
        User currentUser = userRepository.findByUsername(authentication.getName()).orElse(null);

        if (!isAdmin && currentUser != null) {
            userGroupName = currentUser.getGroupName();
            ScheduleEntryDto existingDto = null;
            try {
                existingDto = scheduleService.getEntryById(entryDto.getId());
            } catch (EntityNotFoundException e) {
            }
            if (existingDto != null && (existingDto.getGroupName() == null || !existingDto.getGroupName().equals(userGroupName))) {
                log.warn("Попытка студента {} обновить запись (ID: {}) чужой группы.", authentication.getName(), entryDto.getId());
                redirectAttributes.addFlashAttribute("errorMessage", "Вы не можете изменять записи для другой группы.");
                return "redirect:/schedule";
            }

            if (userGroupName != null) {
                entryDto.setGroupName(userGroupName);
            }
        }

        if (entryDto.getId() == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Не указан идентификатор записи.");
            return "redirect:/schedule";
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("isAdmin", isAdmin);
            populateCommonModelAttributes(model, entryDto.getAcademicYear(), entryDto.getSemester(), entryDto.getWeekNumber(), userGroupName);
            addActivePageAttributes(model);
            return "schedule-form";
        }

        try {
            scheduleService.updateEntry(entryDto.getId(), entryDto);
        } catch (EntityNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Запись не найдена.");
        } catch (Exception e) {
            model.addAttribute("isAdmin", isAdmin);
            populateCommonModelAttributes(model, entryDto.getAcademicYear(), entryDto.getSemester(), entryDto.getWeekNumber(), userGroupName);
            addActivePageAttributes(model);
            return "schedule-form";
        }

        return "redirect:/schedule?academicYear=" + entryDto.getAcademicYear() +
                "&semester=" + entryDto.getSemester() +
                "&week=" + entryDto.getWeekNumber() +
                (isAdmin && entryDto.getGroupName() != null ? "&groupName=" + entryDto.getGroupName() : "");
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
        } catch (Exception e) {
            log.error("Ошибка при получении границ недели через API для {}/{}/{}: {}", academicYear, semester, week, e.getMessage());
            return ResponseEntity.status(500).body(Collections.singletonMap("error", "Не удалось рассчитать границы недели: " + e.getMessage()));
        }
    }
}