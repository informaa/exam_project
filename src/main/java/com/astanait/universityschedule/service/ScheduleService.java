package com.astanait.universityschedule.service;

import com.astanait.universityschedule.model.*;
import com.astanait.universityschedule.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

// Сервис для управления расписанием
@Service
public class ScheduleService {

    // Репозитории для доступа к данным
    private final ScheduleEntryRepository scheduleEntryRepository;
    private final GroupRepository groupRepository;
    private final SubjectRepository subjectRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    public static final int WEEKS_IN_SEMESTER = 16; // Количество недель в семестре
    private static final DateTimeFormatter DATE_FORMATTER_DD_MM = DateTimeFormatter.ofPattern("dd.MM"); // Форматтер для дат (день.месяц)
    private static final Locale RUSSIAN_LOCALE = Locale.forLanguageTag("ru-RU"); // Локаль для корректного определения недели


    // Конструктор для внедрения зависимостей
    public ScheduleService(ScheduleEntryRepository scheduleEntryRepository,
                           GroupRepository groupRepository,
                           SubjectRepository subjectRepository,
                           RoomRepository roomRepository,
                           UserRepository userRepository) {
        this.scheduleEntryRepository = scheduleEntryRepository;
        this.groupRepository = groupRepository;
        this.subjectRepository = subjectRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
    }

    // --- CRUD операции для записей расписания (ScheduleEntry) ---
    // Получить все записи расписания
    public List<ScheduleEntry> getAllScheduleEntries() {
        return scheduleEntryRepository.findAll();
    }

    // Получить запись расписания по ID
    public Optional<ScheduleEntry> getScheduleEntryById(Long id) {
        return scheduleEntryRepository.findById(id);
    }

    // Сохранить запись расписания
    public ScheduleEntry saveScheduleEntry(ScheduleEntry entry) {
        return scheduleEntryRepository.save(entry);
    }

    // Удалить запись расписания по ID
    public void deleteScheduleEntry(Long id) {
        scheduleEntryRepository.deleteById(id);
    }

    // --- Методы для получения расписания по критериям ---

    // Получить расписание для студенческой группы
    public List<ScheduleEntry> getScheduleForStudent(Group group, String academicYear, int semester, int weekInSemester) {
        if (group == null || academicYear == null) return Collections.emptyList(); // Проверка входных данных
        // Поиск записей расписания по учебному году, семестру, неделе и группе
        return scheduleEntryRepository.findByAcademicYearAndSemesterAndWeekInSemesterAndGroupOrderByDayOfWeekAscLessonNumberAsc(
                academicYear, semester, weekInSemester, group
        );
    }

    // Получить расписание для просмотра администратором
    public List<ScheduleEntry> getScheduleForAdminView(String academicYear, int semester, int weekInSemester) {
        if (academicYear == null) return Collections.emptyList(); // Проверка входных данных
        // Поиск записей расписания по учебному году, семестру и неделе с сортировкой
        return scheduleEntryRepository.findByAcademicYearAndSemesterAndWeekInSemesterOrderByGroup_NameAscDayOfWeekAscLessonNumberAsc(
                academicYear, semester, weekInSemester
        );
    }

    // --- Методы для работы с датами и неделями ---

    // Получить дату начала семестра
    public LocalDate getSemesterStartDate(String academicYear, int semester) {
        int startYear = Integer.parseInt(academicYear.split("-")[0]); // Получение года начала учебного года
        if (semester == 1) {
            // Осенний семестр
            return LocalDate.of(startYear, 9, 1);
        } else {
            // Весенний семестр
            return LocalDate.of(startYear + 1, 2, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY));
        }
    }

    // Получить дату окончания семестра
    public LocalDate getSemesterEndDate(String academicYear, int semester) {
        LocalDate semesterStart = getSemesterStartDate(academicYear, semester); // Дата начала семестра
        // Конец семестра через WEEKS_IN_SEMESTER недель, в воскресенье
        return semesterStart.plusWeeks(WEEKS_IN_SEMESTER -1).with(DayOfWeek.SUNDAY);
    }

    // Получить даты дней недели для указанной недели семестра
    public Map<DayOfWeek, String> getWeekDayDates(String academicYear, int semester, int weekInSemester) {
        Map<DayOfWeek, String> weekDayDates = new LinkedHashMap<>(); // Карта для хранения дат (сохраняет порядок вставки)
        LocalDate semesterStart = getSemesterStartDate(academicYear, semester); // Дата начала семестра

        // Понедельник выбранной недели семестра
        LocalDate startOfWeekForSelectedView = semesterStart.plusWeeks(weekInSemester - 1).with(DayOfWeek.MONDAY);

        // Формирование дат для отображаемых дней недели
        for (DayOfWeek day : getDisplayDaysOrder()) {
            weekDayDates.put(day, startOfWeekForSelectedView.plusDays(day.getValue() - 1).format(DATE_FORMATTER_DD_MM));
        }
        return weekDayDates;
    }

    // Получить порядок отображаемых дней недели (понедельник-суббота)
    public List<DayOfWeek> getDisplayDaysOrder() {
        return Arrays.asList(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);
    }

    // Получить диапазон дат для недели
    public String getWeekDateRange(String academicYear, int semester, int weekInSemester) {
        LocalDate semesterStart = getSemesterStartDate(academicYear, semester); // Дата начала семестра
        LocalDate startCurrentWeek = semesterStart.plusWeeks(weekInSemester - 1).with(DayOfWeek.MONDAY); // Начало текущей недели (понедельник)
        LocalDate endCurrentWeek = startCurrentWeek.plusDays(5); // Конец текущей недели (суббота для 6-дневки)
        if (getDisplayDaysOrder().contains(DayOfWeek.SUNDAY)) { // Если 7-дневка (включено воскресенье)
            endCurrentWeek = startCurrentWeek.plusDays(6);
        }
        // Форматирование диапазона дат
        return startCurrentWeek.format(DATE_FORMATTER_DD_MM) + " - " + endCurrentWeek.format(DATE_FORMATTER_DD_MM);
    }


    // --- Логика генерации расписания ---

    // Проверить, занята ли аудитория
    public boolean isRoomOccupied(Room room, DayOfWeek day, int lessonNumber, List<ScheduleEntry> currentWeekScheduleTemplate) {
        for (ScheduleEntry entry : currentWeekScheduleTemplate) {
            if (entry.getRoom().equals(room) && entry.getDayOfWeek() == day && entry.getLessonNumber() == lessonNumber) {
                return true; // Аудитория занята
            }
        }
        return false; // Аудитория свободна
    }

    // Проверить, занят ли преподаватель
    public boolean isTeacherOccupied(AppUser teacher, DayOfWeek day, int lessonNumber, List<ScheduleEntry> currentWeekScheduleTemplate) {
        if (teacher == null) return false; // Если преподаватель не указан, он не занят
        for (ScheduleEntry entry : currentWeekScheduleTemplate) {
            if (entry.getSubject() != null && entry.getSubject().getTeacher() != null &&
                    entry.getSubject().getTeacher().equals(teacher) &&
                    entry.getDayOfWeek() == day &&
                    entry.getLessonNumber() == lessonNumber) {
                return true; // Преподаватель занят
            }
        }
        return false; // Преподаватель свободен
    }

    // Проверить, занята ли группа
    public boolean isGroupOccupied(Group group, DayOfWeek day, int lessonNumber, List<ScheduleEntry> currentWeekScheduleTemplate) {
        for (ScheduleEntry entry : currentWeekScheduleTemplate) {
            if (entry.getGroup().equals(group) && entry.getDayOfWeek() == day && entry.getLessonNumber() == lessonNumber) {
                return true; // Группа занята
            }
        }
        return false; // Группа свободна
    }

    // Генерация и сохранение расписания (транзакционный метод)
    @Transactional
    public boolean generateAndSaveSchedule(String academicYear, int semester, List<Long> subjectIdsForSemester,
                                           List<DayOfWeek> workingDays, int lessonsPerDayFromForm,
                                           boolean repeatWeeklyThroughoutSemester) {
        System.out.println("Начинаем генерацию расписания для: " + academicYear + ", Семестр: " + semester +
                ", Уроков в день: " + lessonsPerDayFromForm);

        // Получение необходимых данных из репозиториев
        List<Group> allGroups = groupRepository.findAll();
        List<Subject> subjectsForThisSemester = subjectRepository.findAllById(subjectIdsForSemester);
        List<Room> allRooms = roomRepository.findAll();

        // Проверка наличия данных
        if (allGroups.isEmpty()) {
            System.err.println("Ошибка: Нет групп для генерации расписания.");
            return false;
        }
        if (subjectsForThisSemester.isEmpty()) {
            System.err.println("Ошибка: Не выбраны предметы для генерации расписания для семестра.");
            return false;
        }
        if (allRooms.isEmpty()) {
            System.err.println("Ошибка: Нет доступных аудиторий для генерации расписания.");
            return false;
        }
        if (workingDays.isEmpty()) {
            System.err.println("Ошибка: Не выбраны рабочие дни для генерации расписания.");
            return false;
        }

        // Очистка предыдущего расписания для данного учебного года и семестра
        System.out.println("Очистка предыдущего расписания для " + academicYear + ", Семестр " + semester + "...");
        scheduleEntryRepository.deleteByAcademicYearAndSemester(academicYear, semester);
        System.out.println("Старое расписание для указанного периода очищено.");

        List<ScheduleEntry> templateWeekEntries = new ArrayList<>(); // Список для хранения шаблонных записей расписания на неделю
        // Карта для отслеживания количества запланированных занятий по предметам для каждой группы
        Map<Long, Map<Long, Integer>> groupSubjectScheduledCounts = new HashMap<>();
        allGroups.forEach(group -> groupSubjectScheduledCounts.put(group.getId(), new HashMap<>())); // Инициализация карты

        // Итерация по группам
        for (Group currentGroup : allGroups) {
            Map<Long, Integer> currentGroupScheduledCounts = groupSubjectScheduledCounts.computeIfAbsent(currentGroup.getId(), k -> new HashMap<>());

            // Итерация по рабочим дням
            for (DayOfWeek day : workingDays) {
                int lessonsPlacedThisDayForGroup = 0; // Счетчик размещенных занятий для группы в текущий день
                // Итерация по номерам занятий
                for (int lessonNum = 1; lessonNum <= lessonsPerDayFromForm; lessonNum++) {
                    // Проверка, занята ли группа в это время
                    if (isGroupOccupied(currentGroup, day, lessonNum, templateWeekEntries)) {
                        System.out.printf("Слот уже занят для Группы %s, День %s, Пара %d. Пропуск.%n", currentGroup.getName(), day, lessonNum);
                        continue;
                    }

                    boolean placedThisSlot = false; // Флаг, указывающий, размещено ли занятие в текущий слот
                    List<Subject> availableSubjectsShuffled = new ArrayList<>(subjectsForThisSemester); // Копия списка предметов для перемешивания
                    Collections.shuffle(availableSubjectsShuffled); // Перемешивание предметов для случайного выбора

                    // Итерация по доступным предметам
                    for (Subject subject : availableSubjectsShuffled) {
                        int scheduledCount = currentGroupScheduledCounts.getOrDefault(subject.getId(), 0); // Количество уже запланированных занятий по этому предмету

                        // Проверка, не превышено ли количество кредитов по предмету
                        if (scheduledCount < subject.getCredits()) {
                            AppUser teacher = subject.getTeacher(); // Преподаватель предмета
                            if (teacher == null) {
                                System.err.printf("Предупреждение: У предмета '%s' не назначен преподаватель. Предмет не может быть запланирован.%n", subject.getName());
                                continue; // Пропуск предмета, если нет преподавателя
                            }

                            // Проверка, свободен ли преподаватель
                            if (!isTeacherOccupied(teacher, day, lessonNum, templateWeekEntries)) {
                                Room room = findAvailableRoom(day, lessonNum, templateWeekEntries, allRooms); // Поиск свободной аудитории
                                if (room != null) {
                                    // Создание шаблонной записи расписания
                                    ScheduleEntry templateEntry = new ScheduleEntry(day, lessonNum, currentGroup, subject, room,
                                            null, 0, 0); // null, 0, 0 - временные значения для шаблона
                                    templateWeekEntries.add(templateEntry); // Добавление в список шаблонов
                                    currentGroupScheduledCounts.put(subject.getId(), scheduledCount + 1); // Обновление счетчика запланированных занятий
                                    lessonsPlacedThisDayForGroup++; // Увеличение счетчика размещенных занятий
                                    placedThisSlot = true; // Установка флага
                                    System.out.printf("Шаблон: Группа %s, День %s, Пара %d: Предмет %s (%s) в ауд. %s%n",
                                            currentGroup.getName(), day, lessonNum, subject.getName(), teacher.getFullName(), room.getRoomNumber());
                                    break; // Переход к следующему слоту
                                }
                            }
                        }
                    }

                    // Если не удалось разместить занятие в текущий слот
                    if (!placedThisSlot) {
                        System.err.printf("Предупреждение: Не удалось найти подходящий предмет/аудиторию/учителя для Группы %s, День %s, Пара %d%n",
                                currentGroup.getName(), day, lessonNum);
                    }
                }
                // Проверка, все ли требуемые занятия размещены для группы в текущий день
                if (lessonsPlacedThisDayForGroup < lessonsPerDayFromForm && lessonsPlacedThisDayForGroup > 0) {
                    System.err.printf("ВНИМАНИЕ: Для Группы %s в %s запланировано только %d из %d требуемых пар.%n",
                            currentGroup.getName(), day, lessonsPlacedThisDayForGroup, lessonsPerDayFromForm);
                } else if (lessonsPerDayFromForm > 0 && lessonsPlacedThisDayForGroup == 0) {
                    System.err.printf("ВНИМАНИЕ: Для Группы %s в %s не удалось запланировать ни одной из %d требуемых пар.%n",
                            currentGroup.getName(), day, lessonsPerDayFromForm);
                }
            }
        }

        checkAllCreditsPlaced(allGroups, subjectsForThisSemester, groupSubjectScheduledCounts); // Проверка распределения кредитов

        List<ScheduleEntry> finalScheduleEntriesToSave = new ArrayList<>(); // Список для итоговых записей расписания
        // Определение количества недель для генерации
        int numberOfWeeksToGenerate = repeatWeeklyThroughoutSemester ? WEEKS_IN_SEMESTER : 1;

        // Создание записей расписания на основе шаблона для каждой недели
        for (int week = 1; week <= numberOfWeeksToGenerate; week++) {
            for (ScheduleEntry template : templateWeekEntries) {
                finalScheduleEntriesToSave.add(new ScheduleEntry(
                        template.getDayOfWeek(), template.getLessonNumber(), template.getGroup(),
                        template.getSubject(), template.getRoom(),
                        academicYear, semester, week // Установка учебного года, семестра и недели
                ));
            }
        }

        // Проверка, были ли созданы записи для сохранения
        if (finalScheduleEntriesToSave.isEmpty() && !templateWeekEntries.isEmpty()){
            System.err.println("КРИТИЧЕСКАЯ ОШИБКА: Шаблонные записи были созданы, но итоговый список для сохранения пуст!");
            return false; // Генерация не удалась
        } else if (templateWeekEntries.isEmpty()){
            System.err.println("Шаблонная неделя пуста. Расписание не было сгенерировано (нет доступных предметов/ресурсов).");
            return false; // Генерация не удалась, если шаблон пуст
        }

        scheduleEntryRepository.saveAll(finalScheduleEntriesToSave); // Сохранение всех записей расписания
        System.out.println("Расписание успешно сгенерировано и сохранено для " + academicYear +
                ", Семестр " + semester +
                (repeatWeeklyThroughoutSemester ? " на " + WEEKS_IN_SEMESTER + " недель." : " на 1 неделю."));
        return true; // Генерация успешна
    }

    // Найти свободную аудиторию
    private Room findAvailableRoom(DayOfWeek day, int lessonNumber, List<ScheduleEntry> currentSchedule, List<Room> allRooms) {
        List<Room> shuffledRooms = new ArrayList<>(allRooms); // Копия списка аудиторий для перемешивания
        Collections.shuffle(shuffledRooms); // Перемешивание аудиторий для случайного выбора
        for (Room room : shuffledRooms) {
            if (!isRoomOccupied(room, day, lessonNumber, currentSchedule)) {
                return room; // Возврат свободной аудитории
            }
        }
        return null; // Если свободная аудитория не найдена
    }

    // Проверить, все ли кредиты по предметам размещены
    private void checkAllCreditsPlaced(List<Group> allGroups, List<Subject> subjectsForSemester, Map<Long, Map<Long, Integer>> groupSubjectScheduledCounts) {
        System.out.println("--- Проверка распределения кредитов после генерации шаблона ---");
        boolean allCreditsFullyPlacedOverall = true; // Флаг, все ли кредиты размещены в целом
        // Итерация по группам
        for (Group group : allGroups) {
            Map<Long, Integer> scheduledCountsForGroup = groupSubjectScheduledCounts.getOrDefault(group.getId(), Collections.emptyMap()); // Запланированные кредиты для группы
            System.out.println("  Для группы: " + group.getName());
            boolean groupCreditsOk = true; // Флаг, все ли кредиты размещены для текущей группы
            // Итерация по предметам семестра
            for (Subject subject : subjectsForSemester) {
                int scheduled = scheduledCountsForGroup.getOrDefault(subject.getId(), 0); // Количество запланированных занятий по предмету
                // Проверка, если у предмета нет преподавателя
                if (subject.getTeacher() == null && subject.getCredits() > 0) {
                    System.err.printf("    - ПРЕДУПРЕЖДЕНИЕ: Предмет '%s' (кредитов: %d) не имеет преподавателя и не мог быть запланирован.%n",
                            subject.getName(), subject.getCredits());
                    if (subject.getCredits() > 0) { // Если кредиты есть, но преподавателя нет
                        allCreditsFullyPlacedOverall = false;
                        groupCreditsOk = false;
                    }
                    continue; // Переход к следующему предмету
                }

                // Проверка, если запланировано меньше кредитов, чем требуется
                if (scheduled < subject.getCredits()) {
                    System.err.printf("    - НЕДОСТАТОЧНО: Предмет: %s, Запланировано: %d/%d кредитов.%n",
                            subject.getName(), scheduled, subject.getCredits());
                    allCreditsFullyPlacedOverall = false;
                    groupCreditsOk = false;
                    // Проверка, если запланировано больше кредитов, чем требуется (информационное сообщение)
                } else if (scheduled > subject.getCredits()) {
                    System.out.println(String.format("    - ИЗБЫТОК: Предмет: %s, Запланировано: %d, Требуется: %d кредитов. (Это нормально, если часов больше кредитов)",
                            subject.getName(), scheduled, subject.getCredits()));
                }
            }
            // Вывод сообщения о распределении кредитов для группы
            if (groupCreditsOk && !subjectsForSemester.isEmpty()) {
                System.out.println("    Все требуемые кредиты для этой группы распределены.");
            } else if (subjectsForSemester.isEmpty()) {
                System.out.println("    Нет предметов для проверки кредитов для этой группы.");
            }
        }
        // Вывод итогового сообщения о распределении кредитов
        if (allCreditsFullyPlacedOverall && !subjectsForSemester.isEmpty()) {
            System.out.println("Все требуемые кредиты предметов для шаблона были успешно распределены по группам.");
        } else if (!subjectsForSemester.isEmpty()) {
            System.err.println("ВНИМАНИЕ: Не все кредиты предметов были полностью распределены по группам. Проверьте логи выше.");
        } else {
            System.out.println("Предметы для семестра не были выбраны, проверка кредитов не проводилась.");
        }
        System.out.println("--- Конец проверки распределения кредитов ---");
    }
}