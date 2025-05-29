package com.astanait.universityschedule.controller;

import com.astanait.universityschedule.model.AppUser;
import com.astanait.universityschedule.model.ScheduleEntry;
import com.astanait.universityschedule.model.UserRole;
import com.astanait.universityschedule.service.CustomUserDetailsService;
import com.astanait.universityschedule.service.GroupService;
import com.astanait.universityschedule.service.ScheduleService;
import com.astanait.universityschedule.service.SubjectService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Controller // Определяет класс как контроллер Spring MVC
@RequestMapping("/schedule") // Сопоставляет HTTP-запросы с путем "/schedule" с этим контроллером
public class ScheduleController {

    private final ScheduleService scheduleService; // Сервис для работы с расписанием
    private final CustomUserDetailsService userDetailsService; // Сервис для работы с пользователями
    private final GroupService groupService; // Сервис для работы с группами
    private final SubjectService subjectService; // Сервис для работы с предметами

    // Карта времени начала занятий
    private static final Map<Integer, LocalTime> LESSON_START_TIMES;
    static {
        LESSON_START_TIMES = new LinkedHashMap<>(); // Используем LinkedHashMap для сохранения порядка вставки
        LESSON_START_TIMES.put(1, LocalTime.of(8, 0)); // 1-е занятие в 8:00
        LESSON_START_TIMES.put(2, LocalTime.of(8, 50)); // 2-е занятие в 8:50
        LESSON_START_TIMES.put(3, LocalTime.of(9, 40)); // 3-е занятие в 9:40
        LESSON_START_TIMES.put(4, LocalTime.of(10, 30)); // 4-е занятие в 10:30
        LESSON_START_TIMES.put(5, LocalTime.of(11, 20)); // 5-е занятие в 11:20
        LESSON_START_TIMES.put(6, LocalTime.of(12, 10)); // 6-е занятие в 12:10
        LESSON_START_TIMES.put(7, LocalTime.of(13, 0)); // 7-е занятие в 13:00
        LESSON_START_TIMES.put(8, LocalTime.of(13, 50)); // 8-е занятие в 13:50
    }
    private static final int LESSON_DURATION_MINUTES = 40; // Продолжительность занятия в минутах

    // Конструктор для внедрения зависимостей
    public ScheduleController(ScheduleService scheduleService, CustomUserDetailsService userDetailsService,
                              GroupService groupService, SubjectService subjectService) {
        this.scheduleService = scheduleService;
        this.userDetailsService = userDetailsService;
        this.groupService = groupService;
        this.subjectService = subjectService;
    }

    // Обрабатывает GET-запросы для просмотра расписания
    @GetMapping
    public String viewSchedule(@AuthenticationPrincipal UserDetails currentUser, Model model,
                               @RequestParam(required = false) String viewAcademicYear, // Необязательный параметр: учебный год
                               @RequestParam(required = false) Integer viewSemester, // Необязательный параметр: семестр
                               @RequestParam(required = false) Integer viewWeek, // Необязательный параметр: неделя
                               @RequestParam(required = false) String generationSuccess) { // Необязательный параметр: флаг успешной генерации
        AppUser appUser = (AppUser) userDetailsService.loadUserByUsername(currentUser.getUsername()); // Получаем текущего пользователя

        // Определяем учебный год, семестр и неделю для отображения
        String defaultAcademicYear = LocalDate.now().getYear() + "-" + (LocalDate.now().getYear() + 1);
        String academicYearToView = (viewAcademicYear != null && !viewAcademicYear.isEmpty()) ? viewAcademicYear : defaultAcademicYear;
        int semesterToView = (viewSemester != null) ? viewSemester : 1;
        int weekToView = (viewWeek != null) ? viewWeek : 1;

        // Отображение сообщения об успешной генерации
        if ("true".equals(generationSuccess) || "true".equalsIgnoreCase(String.valueOf(model.getAttribute("generationSuccess")))) {
            if (model.containsAttribute("successMessage")) {
                // Сообщение уже установлено, ничего не делаем
            } else {
                model.addAttribute("successMessage", "Расписание для " + academicYearToView + ", семестра " + semesterToView + " успешно сгенерировано!");
            }
        }


        List<ScheduleEntry> scheduleEntries; // Список записей расписания
        Map<DayOfWeek, Map<Integer, List<ScheduleEntry>>> scheduleByDayAndLesson = new LinkedHashMap<>(); // Расписание, сгруппированное по дням и номерам занятий

        model.addAttribute("isAdmin", appUser.getRole() == UserRole.ADMIN); // Флаг, является ли пользователь администратором
        model.addAttribute("displayDaysOrder", scheduleService.getDisplayDaysOrder()); // Порядок отображения дней недели
        model.addAttribute("lessonNumbers", IntStream.rangeClosed(1, LESSON_START_TIMES.size()).toArray()); // Номера занятий
        model.addAttribute("lessonStartTimes", LESSON_START_TIMES); // Время начала занятий
        model.addAttribute("lessonDuration", LESSON_DURATION_MINUTES); // Продолжительность занятий

        model.addAttribute("currentAcademicYear", academicYearToView); // Текущий учебный год для отображения
        model.addAttribute("currentSemester", semesterToView); // Текущий семестр для отображения
        model.addAttribute("currentWeek", weekToView); // Текущая неделя для отображения

        model.addAttribute("WEEKS_IN_SEMESTER", ScheduleService.WEEKS_IN_SEMESTER); // Количество недель в семестре

        // Формируем список учебных годов для выбора
        List<String> academicYearsList = new ArrayList<>();
        int currentYear = LocalDate.now().getYear();
        for (int i = -2; i < 3; i++) {
            academicYearsList.add((currentYear + i) + "-" + (currentYear + i + 1));
        }
        Collections.sort(academicYearsList);
        model.addAttribute("allAcademicYears", academicYearsList); // Все доступные учебные годы
        model.addAttribute("allSemesters", Arrays.asList(1, 2)); // Все доступные семестры
        model.addAttribute("allWeeks", IntStream.rangeClosed(1, ScheduleService.WEEKS_IN_SEMESTER).boxed().collect(Collectors.toList())); // Все доступные недели

        Long currentGroupId = null; // ID выбранной группы (для фильтрации администратором)
        // Комментарий: логика получения filterGroupId для администратора (в данный момент закомментирована)
        // @RequestParam(required = false) Long filterGroupId
        // if (filterGroupId != null && filterGroupId > 0 && appUser.getRole() == UserRole.ADMIN) {
        //    currentGroupId = filterGroupId;
        // }
        model.addAttribute("currentGroupId", currentGroupId);


        // Загрузка расписания в зависимости от роли пользователя
        if (appUser.getRole() == UserRole.ADMIN) {
            scheduleEntries = scheduleService.getScheduleForAdminView(academicYearToView, semesterToView, weekToView);
            model.addAttribute("viewTitle", "Общее расписание: " + academicYearToView + ", Семестр " + semesterToView + ", Неделя " + weekToView);
            model.addAttribute("allGroups", groupService.getAllGroups()); // Все группы для администратора
        } else if (appUser.getRole() == UserRole.STUDENT) {
            if (appUser.getStudentGroup() == null) {
                model.addAttribute("errorMessage", "Ваша группа не назначена. Обратитесь к администратору.");
                scheduleEntries = Collections.emptyList();
            } else {
                scheduleEntries = scheduleService.getScheduleForStudent(appUser.getStudentGroup(), academicYearToView, semesterToView, weekToView);
                model.addAttribute("groupName", appUser.getStudentGroup().getName());
                model.addAttribute("viewTitle", "Расписание группы: " + appUser.getStudentGroup().getName() +
                        " (" + academicYearToView + ", Сем: " + semesterToView + ", Нед: " + weekToView + ")");
            }
        } else { // Для других ролей (например, преподаватель, если будет добавлено)
            scheduleEntries = Collections.emptyList();
            model.addAttribute("viewTitle", "Расписание для: " + appUser.getFullName() +
                    " (" + academicYearToView + ", Сем: " + semesterToView + ", Нед: " + weekToView + ")");
        }

        // Группировка записей расписания по дням недели и номерам занятий
        if (!scheduleEntries.isEmpty()) {
            scheduleByDayAndLesson = scheduleEntries.stream()
                    .collect(Collectors.groupingBy(ScheduleEntry::getDayOfWeek,
                            LinkedHashMap::new, // Сохраняем порядок дней
                            Collectors.groupingBy(ScheduleEntry::getLessonNumber,
                                    LinkedHashMap::new, // Сохраняем порядок занятий
                                    Collectors.toList())));
        }
        model.addAttribute("scheduleByDayAndLesson", scheduleByDayAndLesson); // Сгруппированное расписание
        model.addAttribute("loggedInUser", appUser); // Информация о текущем пользователе


        // Получение дат для каждого дня отображаемой недели
        Map<DayOfWeek, String> weekDayDatesMap = scheduleService.getWeekDayDates(academicYearToView, semesterToView, weekToView);
        model.addAttribute("weekDayDatesMap", weekDayDatesMap);

        // Получение диапазона дат для отображаемой недели
        String weekDateRange = scheduleService.getWeekDateRange(academicYearToView, semesterToView, weekToView);
        model.addAttribute("weekDateRange", weekDateRange);

        // Даты начала и конца семестра для отображения
        model.addAttribute("semesterStartDateDisplay", scheduleService.getSemesterStartDate(academicYearToView, semesterToView).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        model.addAttribute("semesterEndDateDisplay", scheduleService.getSemesterEndDate(academicYearToView, semesterToView).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")));

        return "schedule/schedule-view"; // Возвращает имя представления для отображения расписания
    }

    // Обрабатывает GET-запросы для отображения формы генерации расписания
    @GetMapping("/generate-form")
    public String showGenerateForm(Model model) {
        // Установка значений по умолчанию для формы, если они не были переданы (например, после ошибки)
        if (!model.containsAttribute("selectedAcademicYear")) {
            int currentYr = LocalDate.now().getYear();
            model.addAttribute("selectedAcademicYear", currentYr + "-" + (currentYr + 1));
        }
        if (!model.containsAttribute("selectedSemester")) {
            model.addAttribute("selectedSemester", 1);
        }
        if (!model.containsAttribute("selectedLessonsPerDay")) {
            model.addAttribute("selectedLessonsPerDay", 6);
        }
        // Установка рабочих дней по умолчанию
        if (!model.containsAttribute("preSelectedWorkingDays")) {
            List<String> defaultWorkingDays = Arrays.asList(
                    DayOfWeek.MONDAY.name(), DayOfWeek.TUESDAY.name(), DayOfWeek.WEDNESDAY.name(),
                    DayOfWeek.THURSDAY.name(), DayOfWeek.FRIDAY.name()
            );
            model.addAttribute("preSelectedWorkingDays", defaultWorkingDays);
        } else {
            // Обработка случая, когда атрибут есть, но он null
            if (model.getAttribute("preSelectedWorkingDays") == null) {
                model.addAttribute("preSelectedWorkingDays", Collections.emptyList());
            }
        }
        if (!model.containsAttribute("preSelectedSubjectIds")) {
            model.addAttribute("preSelectedSubjectIds", Collections.emptyList());
        }
        if (!model.containsAttribute("preRepeatWeekly")) {
            model.addAttribute("preRepeatWeekly", true);
        }

        model.addAttribute("allSubjects", subjectService.getAllSubjects()); // Все доступные предметы
        // Формируем список учебных годов для формы
        List<String> academicYearsList = new ArrayList<>();
        int currentYr = LocalDate.now().getYear();
        for (int i = -1; i < 3; i++) {
            academicYearsList.add((currentYr + i) + "-" + (currentYr + i + 1));
        }
        Collections.sort(academicYearsList);
        model.addAttribute("academicYears", academicYearsList); // Учебные годы для формы

        model.addAttribute("semesters", Arrays.asList(1, 2)); // Семестры для формы
        model.addAttribute("daysOfWeek", DayOfWeek.values()); // Дни недели для формы
        model.addAttribute("lessonStartTimes", LESSON_START_TIMES); // Время начала занятий для информации на форме
        model.addAttribute("lessonDuration", LESSON_DURATION_MINUTES); // Длительность занятий для информации на форме

        // Инициализация сообщений, если их нет
        if (!model.containsAttribute("successMessage")) model.addAttribute("successMessage", null);
        if (!model.containsAttribute("errorMessage")) model.addAttribute("errorMessage", null);

        return "schedule/generate-form"; // Возвращает имя представления для формы генерации
    }

    // Обрабатывает POST-запросы для генерации расписания
    @PostMapping("/generate")
    public String generateSchedule(@RequestParam String academicYear, // Учебный год
                                   @RequestParam int semester, // Семестр
                                   @RequestParam(required = false) List<Long> subjectIds, // ID выбранных предметов
                                   @RequestParam(required = false) List<String> selectedWorkingDaysStr, // Выбранные рабочие дни (строки)
                                   @RequestParam int lessonsPerDay, // Количество занятий в день
                                   @RequestParam(defaultValue = "false") boolean repeatWeeklyThroughoutSemester, // Флаг повторения расписания еженедельно
                                   Model model,
                                   RedirectAttributes redirectAttributes) { // Для передачи flash-атрибутов при редиректе
        System.out.println("DEBUG: Вызов generateSchedule (POST)"); // Отладочное сообщение

        // Валидация: выбраны ли рабочие дни
        if (selectedWorkingDaysStr == null || selectedWorkingDaysStr.isEmpty()) {
            model.addAttribute("errorMessage", "Пожалуйста, выберите хотя бы один рабочий день.");
            repopulateFormModelOnError(model, academicYear, semester, subjectIds, selectedWorkingDaysStr, lessonsPerDay, repeatWeeklyThroughoutSemester); // Восстанавливаем значения формы
            return "schedule/generate-form"; // Возвращаемся на форму с ошибкой
        }
        // Преобразование строк рабочих дней в объекты DayOfWeek
        List<DayOfWeek> workingDays = selectedWorkingDaysStr.stream()
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toList());

        // Валидация: выбраны ли предметы
        if (subjectIds == null || subjectIds.isEmpty()) {
            model.addAttribute("errorMessage", "Пожалуйста, выберите предметы для генерации расписания.");
            repopulateFormModelOnError(model, academicYear, semester, subjectIds, selectedWorkingDaysStr, lessonsPerDay, repeatWeeklyThroughoutSemester); // Восстанавливаем значения формы
            return "schedule/generate-form"; // Возвращаемся на форму с ошибкой
        }

        try {
            // Вызов сервиса для генерации и сохранения расписания
            boolean success = scheduleService.generateAndSaveSchedule(academicYear, semester, subjectIds, workingDays, lessonsPerDay, repeatWeeklyThroughoutSemester);
            if (success) {
                // В случае успеха устанавливаем flash-атрибуты для отображения сообщения на странице просмотра
                redirectAttributes.addFlashAttribute("generationSuccess", "true");
                redirectAttributes.addFlashAttribute("successMessage", "Расписание для " + academicYear + ", семестра " + semester + " успешно сгенерировано!");
                return "redirect:/schedule?viewAcademicYear=" + academicYear + "&viewSemester=" + semester + "&viewWeek=1"; // Редирект на страницу просмотра с параметрами
            } else {
                model.addAttribute("errorMessage", "Не удалось полностью сгенерировать расписание. Проверьте консоль и данные (например, достаточно ли аудиторий, назначены ли преподаватели предметам).");
                repopulateFormModelOnError(model, academicYear, semester, subjectIds, selectedWorkingDaysStr, lessonsPerDay, repeatWeeklyThroughoutSemester); // Восстанавливаем значения формы
                return "schedule/generate-form"; // Возвращаемся на форму с ошибкой
            }
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Произошла непредвиденная ошибка при генерации: " + e.getMessage());
            e.printStackTrace(); // Вывод стека ошибки в консоль
            repopulateFormModelOnError(model, academicYear, semester, subjectIds, selectedWorkingDaysStr, lessonsPerDay, repeatWeeklyThroughoutSemester); // Восстанавливаем значения формы
            return "schedule/generate-form"; // Возвращаемся на форму с ошибкой
        }
    }

    // Приватный метод для восстановления данных формы в случае ошибки генерации
    private void repopulateFormModelOnError(Model model, String academicYear, int semester, List<Long> subjectIds,
                                            List<String> selectedWorkingDaysStr, int lessonsPerDay,
                                            boolean repeatWeeklyThroughoutSemester) {
        model.addAttribute("selectedAcademicYear", academicYear); // Восстанавливаем выбранный учебный год
        model.addAttribute("selectedSemester", semester); // Восстанавливаем выбранный семестр
        model.addAttribute("selectedLessonsPerDay", lessonsPerDay); // Восстанавливаем выбранное количество занятий в день
        model.addAttribute("preSelectedWorkingDays", selectedWorkingDaysStr != null ? selectedWorkingDaysStr : Collections.emptyList()); // Восстанавливаем выбранные рабочие дни
        model.addAttribute("preSelectedSubjectIds", subjectIds != null ? subjectIds : Collections.emptyList()); // Восстанавливаем выбранные предметы
        model.addAttribute("preRepeatWeekly", repeatWeeklyThroughoutSemester); // Восстанавливаем флаг еженедельного повторения

        // Повторно добавляем в модель данные, необходимые для отображения формы
        model.addAttribute("allSubjects", subjectService.getAllSubjects());
        List<String> academicYearsList = new ArrayList<>();
        int currentYr = LocalDate.now().getYear();
        for (int i = -1; i < 3; i++) {
            academicYearsList.add((currentYr + i) + "-" + (currentYr + i + 1));
        }
        Collections.sort(academicYearsList);
        model.addAttribute("academicYears", academicYearsList);
        model.addAttribute("semesters", Arrays.asList(1, 2));
        model.addAttribute("daysOfWeek", DayOfWeek.values());
        model.addAttribute("lessonStartTimes", LESSON_START_TIMES);
        model.addAttribute("lessonDuration", LESSON_DURATION_MINUTES);
    }
}