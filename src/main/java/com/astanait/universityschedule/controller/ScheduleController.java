package com.astanait.universityschedule.controller;

import com.astanait.universityschedule.dto.ScheduleEntryDto;
import com.astanait.universityschedule.service.ScheduleService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
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

    @Autowired
    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    // Метод для добавления флагов активной страницы
    private void addActivePageAttributes(Model model) {
        model.addAttribute("isSchedulePageActive", true);
        model.addAttribute("isExamsPageActive", false);
    }

    // Метод для общих атрибутов модели
    private void populateCommonModelAttributes(Model model, String selectedAcademicYear, Integer selectedSemester, Integer selectedWeekNumberForForm) {
        // Данные для выпадающих списков
        model.addAttribute("academicYears", Arrays.asList("2024-2025", "2025-2026")); // Пример
        model.addAttribute("semesters", Arrays.asList(1, 2)); // Пример
        List<Integer> weeks = new ArrayList<>();
        for (int i = 1; i <= 15; i++) weeks.add(i);
        model.addAttribute("weekNumbers", weeks);

        // Отображение информационных дат начала/конца семестра
        String displaySemesterStartDate = "N/A";
        String displaySemesterEndDate = "N/A";
        String yearToUseForSemesterDisplay = selectedAcademicYear;
        Integer semesterToUseForSemesterDisplay = selectedSemester;

        if (yearToUseForSemesterDisplay == null || semesterToUseForSemesterDisplay == null) {
            yearToUseForSemesterDisplay = "2024-2025"; // Дефолтный год
            semesterToUseForSemesterDisplay = 2;      // Дефолтный семестр (весна 2025 для примера)
        }
        String semesterDisplayKey = yearToUseForSemesterDisplay + "_" + semesterToUseForSemesterDisplay;
        LocalDate semesterStartDateValue = ScheduleService.SEMESTER_START_DATES.get(semesterDisplayKey);

        if (semesterStartDateValue != null) {
            displaySemesterStartDate = semesterStartDateValue.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            // Расчет конца семестра (пример, настройте под вашу логику или фиксированные даты)
            if (yearToUseForSemesterDisplay.equals("2024-2025") && semesterToUseForSemesterDisplay == 2) {
                displaySemesterEndDate = LocalDate.of(2025,5,17).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            } else {
                displaySemesterEndDate = semesterStartDateValue.plusWeeks(14).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            }
        }
        model.addAttribute("semesterStartDate", displaySemesterStartDate);
        model.addAttribute("semesterEndDate", displaySemesterEndDate);

        // Параметры для кнопки "Отмена" на форме schedule-form
        model.addAttribute("cancelFormYear", selectedAcademicYear);
        model.addAttribute("cancelFormSemester", selectedSemester);
        model.addAttribute("cancelFormWeek", selectedWeekNumberForForm);

        // Передача min/max/default для полей datetime-local в ФОРМЕ
        if (selectedAcademicYear != null && selectedSemester != null && selectedWeekNumberForForm != null) {
            try {
                LocalDate startOfSelectedWeek = scheduleService.calculateStartDateForAcademicWeek(selectedAcademicYear, selectedSemester, selectedWeekNumberForForm);
                LocalDate endOfSelectedWeek = startOfSelectedWeek.plusDays(6);
                String dateTimeFormatPattern = "yyyy-MM-dd'T'HH:mm";

                model.addAttribute("minDateTimeForWeek", startOfSelectedWeek.atStartOfDay().format(DateTimeFormatter.ofPattern(dateTimeFormatPattern)));
                model.addAttribute("maxDateTimeForWeek", endOfSelectedWeek.atTime(23, 59).format(DateTimeFormatter.ofPattern(dateTimeFormatPattern)));
                model.addAttribute("defaultStartDateTimeForWeek", startOfSelectedWeek.atTime(8,0).format(DateTimeFormatter.ofPattern(dateTimeFormatPattern)));
                log.info("Для формы (schedule-form) переданы границы: Год {}, Сем {}, Неделя {}. Min: {}, Max: {}, DefaultStart: {}",
                        selectedAcademicYear, selectedSemester, selectedWeekNumberForForm,
                        model.getAttribute("minDateTimeForWeek"),
                        model.getAttribute("maxDateTimeForWeek"),
                        model.getAttribute("defaultStartDateTimeForWeek"));
            } catch (Exception e) {
                log.error("Ошибка при вычислении границ дат для schedule-form: Год {}, Сем {}, Неделя {}. Ошибка: {}",
                        selectedAcademicYear, selectedSemester, selectedWeekNumberForForm, e.getMessage());
            }
        }
    }

    @GetMapping
    public String showSchedule(
            @RequestParam(value = "academicYear", required = false) String academicYear,
            @RequestParam(value = "semester", required = false) Integer semester,
            @RequestParam(value = "week", required = false) Integer weekNumber,
            @RequestParam(value = "calendarWeek", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate requestedCalendarDate,
            Model model) {

        log.info(">>> Запрос на отображение расписания. Год: [{}], Семестр: [{}], Акад.Неделя: [{}], Календ.Неделя: [{}]",
                academicYear, semester, weekNumber, requestedCalendarDate);

        populateCommonModelAttributes(model, academicYear, semester, null); // weekNumberForForm здесь null, т.к. это для основной страницы
        addActivePageAttributes(model);

        List<ScheduleEntryDto> entries;
        LocalDate displayWeekStart;
        boolean academicFilterFullyApplied = academicYear != null && !academicYear.isEmpty() &&
                semester != null && weekNumber != null;

        if (academicFilterFullyApplied) {
            log.info("--- Применена полная АКАДЕМИЧЕСКАЯ фильтрация: Год={}, Семестр={}, Неделя={}", academicYear, semester, weekNumber);
            entries = scheduleService.getEntriesByAcademicCriteria(academicYear, semester, weekNumber);
            displayWeekStart = scheduleService.calculateStartDateForAcademicWeek(academicYear, semester, weekNumber);
            model.addAttribute("selectedAcademicYear", academicYear);
            model.addAttribute("selectedSemester", semester);
            model.addAttribute("selectedWeekNumber", weekNumber);
            log.info("--- Рассчитанный displayWeekStart по академ. критериям: {}", displayWeekStart);
        } else if (requestedCalendarDate != null) {
            log.info("--- Применена КАЛЕНДАРНАЯ фильтрация по дате: {}", requestedCalendarDate);
            displayWeekStart = requestedCalendarDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            entries = scheduleService.getEntriesForWeek(displayWeekStart);
        } else {
            log.info("--- Фильтры не применены или не полны, используется текущая КАЛЕНДАРНАЯ неделя.");
            displayWeekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            entries = scheduleService.getEntriesForWeek(displayWeekStart);
        }

        model.addAttribute("currentWeekStart", displayWeekStart);
        log.info("--- Итоговый displayWeekStart для сетки календаря: {}", displayWeekStart);
        log.info("--- Количество записей для отображения в сетке: {}", (entries != null ? entries.size() : "null"));

        List<LocalDate> weekDates = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            weekDates.add(displayWeekStart.plusDays(i));
        }
        model.addAttribute("weekDates", weekDates);

        List<LocalTime> timeSlots = new ArrayList<>();
        for (int hour = 8; hour < 20; hour++) { // Примерный диапазон часов
            timeSlots.add(LocalTime.of(hour, 0));
        }
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

    // Отображение формы для создания новой записи расписания
    @GetMapping("/new")
    public String showCreateForm(Model model) {
        log.debug("Запрос формы для создания новой записи (GET /schedule/new)");
        ScheduleEntryDto newEntry = new ScheduleEntryDto();
        model.addAttribute("entry", newEntry);
        model.addAttribute("pageTitle", "Добавить новую запись в расписание");
        populateCommonModelAttributes(model, newEntry.getAcademicYear(), newEntry.getSemester(), newEntry.getWeekNumber());
        addActivePageAttributes(model);
        return "schedule-form";
    }

    // Обработка сохранения новой записи расписания
    @PostMapping("/save")
    public String saveEntry(@Valid @ModelAttribute("entry") ScheduleEntryDto entryDto,
                            BindingResult bindingResult,
                            RedirectAttributes redirectAttributes,
                            Model model) {
        log.info("Попытка сохранения новой записи расписания: {}", entryDto);
        if (bindingResult.hasErrors()) {
            log.warn("Ошибки валидации при сохранении записи расписания: {}", bindingResult.getAllErrors());
            model.addAttribute("pageTitle", "Добавить новую запись в расписание (Ошибка!)");
            populateCommonModelAttributes(model, entryDto.getAcademicYear(), entryDto.getSemester(), entryDto.getWeekNumber());
            addActivePageAttributes(model);
            return "schedule-form";
        }
        try {
            scheduleService.createEntry(entryDto);
            redirectAttributes.addFlashAttribute("successMessage", "Запись успешно добавлена!");
            log.info("Новая запись расписания успешно сохранена.");
            return "redirect:/schedule?academicYear=" + entryDto.getAcademicYear() +
                    "&semester=" + entryDto.getSemester() +
                    "&week=" + entryDto.getWeekNumber();
        } catch (Exception e) {
            log.error("Ошибка при сохранении записи расписания: {}", e.getMessage(), e);
            model.addAttribute("errorMessage", "Ошибка при сохранении записи: " + e.getMessage());
            model.addAttribute("pageTitle", "Добавить новую запись в расписание (Ошибка!)");
            populateCommonModelAttributes(model, entryDto.getAcademicYear(), entryDto.getSemester(), entryDto.getWeekNumber());
            addActivePageAttributes(model);
            return "schedule-form";
        }
    }

    // Отображение формы для редактирования существующей записи расписания
    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        log.debug("Запрос формы для редактирования записи расписания ID: {}", id);
        try {
            ScheduleEntryDto entryDto = scheduleService.getEntryById(id);
            model.addAttribute("entry", entryDto);
            model.addAttribute("pageTitle", "Редактировать запись расписания (ID: " + id + ")");
            populateCommonModelAttributes(model, entryDto.getAcademicYear(), entryDto.getSemester(), entryDto.getWeekNumber());
            addActivePageAttributes(model);
            return "schedule-form";
        } catch (EntityNotFoundException e) {
            log.warn("Запись расписания с ID {} не найдена для редактирования.", id);
            redirectAttributes.addFlashAttribute("errorMessage", "Запись с ID " + id + " не найдена.");
            return "redirect:/schedule";
        }
    }

    // Обработка обновления существующей записи расписания
    @PostMapping("/update")
    public String updateEntry(@Valid @ModelAttribute("entry") ScheduleEntryDto entryDto,
                              BindingResult bindingResult,
                              RedirectAttributes redirectAttributes,
                              Model model) {
        log.info("Попытка обновления записи расписания ID {}: {}", entryDto.getId(), entryDto);
        if (entryDto.getId() == null) {
            log.error("Попытка обновления записи расписания без ID!");
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка: ID записи не указан для обновления.");
            return "redirect:/schedule";
        }
        if (bindingResult.hasErrors()) {
            log.warn("Ошибки валидации при обновлении записи расписания ID {}: {}", entryDto.getId(), bindingResult.getAllErrors());
            model.addAttribute("pageTitle", "Редактировать запись расписания (ID: " + entryDto.getId() + ") (Ошибка!)");
            populateCommonModelAttributes(model, entryDto.getAcademicYear(), entryDto.getSemester(), entryDto.getWeekNumber());
            addActivePageAttributes(model);
            return "schedule-form";
        }
        try {
            scheduleService.updateEntry(entryDto.getId(), entryDto);
            redirectAttributes.addFlashAttribute("successMessage", "Запись успешно обновлена!");
            log.info("Запись расписания ID {} успешно обновлена.", entryDto.getId());
            return "redirect:/schedule?academicYear=" + entryDto.getAcademicYear() +
                    "&semester=" + entryDto.getSemester() +
                    "&week=" + entryDto.getWeekNumber();
        } catch (EntityNotFoundException e) {
            log.warn("Запись расписания с ID {} не найдена для обновления.", entryDto.getId());
            redirectAttributes.addFlashAttribute("errorMessage", "Запись с ID " + entryDto.getId() + " не найдена.");
            return "redirect:/schedule";
        } catch (Exception e) {
            log.error("Ошибка при обновлении записи расписания ID {}: {}", entryDto.getId(), e.getMessage(), e);
            model.addAttribute("errorMessage", "Ошибка при обновлении: " + e.getMessage());
            model.addAttribute("pageTitle", "Редактировать запись расписания (ID: " + entryDto.getId() + ") (Ошибка!)");
            populateCommonModelAttributes(model, entryDto.getAcademicYear(), entryDto.getSemester(), entryDto.getWeekNumber());
            addActivePageAttributes(model);
            return "schedule-form";
        }
    }

    // Обработка удаления записи расписания
    @GetMapping("/delete/{id}")
    public String deleteEntry(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Запрос на удаление записи расписания ID: {}", id);
        String targetRedirectUrl = "/schedule";
        try {
            ScheduleEntryDto entryDto = scheduleService.getEntryById(id);
            if (entryDto != null && entryDto.getAcademicYear() != null && entryDto.getSemester() != null && entryDto.getWeekNumber() != null) {
                targetRedirectUrl = "/schedule?academicYear=" + entryDto.getAcademicYear() +
                        "&semester=" + entryDto.getSemester() +
                        "&week=" + entryDto.getWeekNumber();
            }
            scheduleService.deleteEntry(id);
            redirectAttributes.addFlashAttribute("successMessage", "Запись ID " + id + " успешно удалена.");
            log.info("Запись расписания ID {} успешно удалена.", id);
        } catch (EntityNotFoundException e) {
            log.warn("Запись расписания с ID {} не найдена для удаления.", id);
            redirectAttributes.addFlashAttribute("errorMessage", "Запись с ID " + id + " не найдена для удаления.");
        } catch (Exception e) {
            log.error("Ошибка при удалении записи расписания ID {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка при удалении записи ID " + id + ": " + e.getMessage());
        }
        return "redirect:" + targetRedirectUrl;
    }

    // API эндпоинт для получения границ дат для JavaScript
    @GetMapping("/api/week-boundaries")
    @ResponseBody
    public ResponseEntity<Map<String, String>> getWeekBoundaries(
            @RequestParam(required = false) String academicYear,
            @RequestParam(required = false) Integer semester,
            @RequestParam(required = false) Integer week) { // 'week' соответствует weekNumber

        if (academicYear == null || academicYear.isEmpty() || semester == null || week == null) {
            log.warn("API /week-boundaries: Не все параметры предоставлены (Год: {}, Сем: {}, Неделя: {}).", academicYear, semester, week);
            return ResponseEntity.ok(Collections.emptyMap()); // Возвращаем пустую карту, JS должен это обработать
        }

        Map<String, String> boundaries = new HashMap<>();
        try {
            LocalDate startOfSelectedWeek = scheduleService.calculateStartDateForAcademicWeek(academicYear, semester, week);
            LocalDate endOfSelectedWeek = startOfSelectedWeek.plusDays(6); // Понедельник + 6 дней = Воскресенье
            String dateTimeFormatPattern = "yyyy-MM-dd'T'HH:mm"; // Формат для datetime-local

            boundaries.put("minDateTime", startOfSelectedWeek.atStartOfDay().format(DateTimeFormatter.ofPattern(dateTimeFormatPattern)));
            boundaries.put("maxDateTime", endOfSelectedWeek.atTime(23, 59).format(DateTimeFormatter.ofPattern(dateTimeFormatPattern)));
            boundaries.put("defaultStartTime", startOfSelectedWeek.atTime(8, 0).format(DateTimeFormatter.ofPattern(dateTimeFormatPattern))); // Например, 8 утра

            log.info("API /week-boundaries: Год {}, Сем {}, Неделя {}. Отправлены границы: {}", academicYear, semester, week, boundaries);
            return ResponseEntity.ok(boundaries);

        } catch (Exception e) {
            log.error("Ошибка при получении границ недели через API для {}/{}/{}: {}", academicYear, semester, week, e.getMessage());
            // Возвращаем ошибку или пустой объект, чтобы клиент мог это обработать
            return ResponseEntity.status(500).body(Collections.singletonMap("error", "Не удалось рассчитать границы недели: " + e.getMessage()));
        }
    }
}