package com.astanait.universityschedule.controller;

import com.astanait.universityschedule.dto.ExamScheduleEntryDto;
import com.astanait.universityschedule.model.User;
import com.astanait.universityschedule.repository.UserRepository;
import com.astanait.universityschedule.service.ExamScheduleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Controller
@RequestMapping("/exams")
public class ExamScheduleController {

    private static final Logger log = LoggerFactory.getLogger(ExamScheduleController.class);
    private final ExamScheduleService examService;
    private final UserRepository userRepository;

    @Autowired
    public ExamScheduleController(ExamScheduleService examService, UserRepository userRepository) {
        this.examService = examService;
        this.userRepository = userRepository;
    }

    private void populateExamPageCommonAttributes(Model model, String currentUserGroupName, boolean isAdmin) {
        int currentYear = LocalDate.now().getYear();
        model.addAttribute("academicYears", Arrays.asList(currentYear + 1, currentYear, currentYear - 1, currentYear - 2));
        model.addAttribute("academicPeriods", Arrays.asList(1, 2));
        model.addAttribute("isSchedulePageActive", false);
        model.addAttribute("isExamsPageActive", true);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("currentUserGroupName", currentUserGroupName);
        model.addAttribute("availableGroups", List.of("ВТ-23А", "ВТ-23Б", "ПИ-22А", "ИС-21А"));
    }

    @GetMapping
    public String showExamSchedulePage(
            @RequestParam(value = "year", required = false) Integer filterAcademicYear,
            @RequestParam(value = "period", required = false) Integer filterAcademicPeriod,
            @RequestParam(value = "groupName", required = false) String filterGroupNameFromRequest,
            Model model,
            Authentication authentication) {

        log.info("Запрос страницы расписания экзаменов. Фильтры: Год [{}], Период [{}], Группа из запроса [{}]",
                filterAcademicYear, filterAcademicPeriod, filterGroupNameFromRequest);

        String currentPrincipalName = authentication.getName();
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_ADMIN"));

        String userGroupName = null;
        User currentUser = userRepository.findByUsername(currentPrincipalName).orElse(null);
        if (currentUser != null && !isAdmin) {
            userGroupName = currentUser.getGroupName();
        }

        String groupNameToFilterBy;
        if (isAdmin) {
            if (filterGroupNameFromRequest != null && !filterGroupNameFromRequest.isBlank()) {
                groupNameToFilterBy = filterGroupNameFromRequest;
                model.addAttribute("selectedGroupName", filterGroupNameFromRequest);
            } else {
                groupNameToFilterBy = null;
            }
        } else {
            groupNameToFilterBy = userGroupName;
        }

        if (filterAcademicYear == null) {
            filterAcademicYear = LocalDate.now().getYear();
            log.debug("Год для фильтра не указан, используется по умолчанию: {}", filterAcademicYear);
        }
        if (filterAcademicPeriod == null) {
            filterAcademicPeriod = 1;
            log.debug("Период для фильтра не указан, используется по умолчанию: {}", filterAcademicPeriod);
        }

        populateExamPageCommonAttributes(model, userGroupName, isAdmin);

        List<ExamScheduleEntryDto> exams = examService.getExamEntries(filterAcademicYear, filterAcademicPeriod, groupNameToFilterBy, isAdmin);

        model.addAttribute("exams", exams);
        model.addAttribute("selectedYear", filterAcademicYear);
        model.addAttribute("selectedPeriod", filterAcademicPeriod);
        model.addAttribute("pageTitle", "Расписание экзаменов");

        log.info("Отображение {} записей экзаменов для Год={}, Период={}, Группа для фильтра={}",
                (exams != null ? exams.size() : 0), filterAcademicYear, filterAcademicPeriod, groupNameToFilterBy);

        return "exam-schedule";
    }
}