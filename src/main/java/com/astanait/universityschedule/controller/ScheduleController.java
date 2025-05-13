package com.astanait.universityschedule.controller;

import com.astanait.universityschedule.dto.ScheduleEntryDto;
import com.astanait.universityschedule.service.ScheduleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;


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
}