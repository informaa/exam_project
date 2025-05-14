package com.astanait.universityschedule.controller;

import com.astanait.universityschedule.dto.ScheduleEntryDto;
import com.astanait.universityschedule.service.ScheduleService;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import jakarta.validation.Valid;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;


@Controller // говорит Spring, что это контроллер, который возвращает HTML-страницы
@RequestMapping("/schedule")
public class ScheduleController {

    private static final Logger log = LoggerFactory.getLogger(ScheduleController.class);
    private final ScheduleService scheduleService;

    @Autowired
    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    private void addActivePageAttributes(Model model) {
        model.addAttribute("isSchedulePageActive", true);
        model.addAttribute("isExamsPageActive", false);
    }

    private void populateCommonModelAttributes(Model model, String selectedAcademicYear, Integer selectedSemester, Integer selectedWeekNumberForForm) {
        model.addAttribute("academicYears", Arrays.asList("2024-2025", "2025-2026"));
        model.addAttribute("semesters", Arrays.asList(1, 2));
        List<Integer> weeks = new ArrayList<>();
        for (int i = 1; i <= 15; i++) weeks.add(i);
        model.addAttribute("weekNumbers", weeks);
    }

    @GetMapping
    public String showSchedule(
            @RequestParam(value = "calendarWeek", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate requestedCalendarDate,
            Model model) {

        log.info("Запрос на отображение основного расписания. Запрошенная календарная неделя (дата из нее): {}", requestedCalendarDate);
        addActivePageAttributes(model);
        populateCommonModelAttributes(model, null, null, null);

        LocalDate displayWeekStart;

        if (requestedCalendarDate != null) {
            displayWeekStart = requestedCalendarDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        } else {
            displayWeekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }
        model.addAttribute("currentWeekStart", displayWeekStart);

        List<ScheduleEntryDto> entries = scheduleService.getEntriesForWeek(displayWeekStart);

        log.info("Отображается неделя, начинающаяся с {}. Записей для этой недели: {}", displayWeekStart, (entries != null ? entries.size() : 0));

        List<LocalDate> weekDates = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            weekDates.add(displayWeekStart.plusDays(i));
        }
        model.addAttribute("weekDates", weekDates);

        List<LocalTime> timeSlots = new ArrayList<>();
        for (int hour = 8; hour < 20; hour++) {
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
        model.addAttribute("entries", entries);
        model.addAttribute("locale", new Locale("ru"));

        return "schedule-weekly";
    }

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

}