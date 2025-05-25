package com.astanait.universityschedule.service;

import com.astanait.universityschedule.dto.ScheduleEntryDto;
import com.astanait.universityschedule.model.ScheduleEntry;
import com.astanait.universityschedule.repository.ScheduleEntryRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Это Spring-сервис (`@Service`), доступный через внедрение зависимостей (`@Autowired`)
@Service
public class ScheduleService {

    // подключение к базе данных
    private final ScheduleEntryRepository repository;
    // для записи логов
    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);

    // Статическая карта, где ключ — строка вида `"год_семестр"`, а значение — дата начала семестра
    public static final Map<String, LocalDate> SEMESTER_START_DATES = new HashMap<>();
    static {
        SEMESTER_START_DATES.put("2024-2025_1", LocalDate.of(2024, 9, 2));
        SEMESTER_START_DATES.put("2024-2025_2", LocalDate.of(2025, 2, 3));
        SEMESTER_START_DATES.put("2025-2026_1", LocalDate.of(2025, 9, 1));
        SEMESTER_START_DATES.put("2025-2026_2", LocalDate.of(2026, 2, 2));
    }

    // Spring внедряет репозиторий в конструктор — стандартный способ зависимостей
    @Autowired
    public ScheduleService(ScheduleEntryRepository scheduleEntryRepository) {
        this.repository = scheduleEntryRepository;
    }

    // Метод принимает год, семестр и неделю, возвращает дату начала учебной недели
    public LocalDate calculateStartDateForAcademicWeek(String academicYear, int semester, int weekNumber) {
       // Формируем ключ для поиска даты начала семестра, например: `"2024-2025_1"`
        String key = academicYear + "_" + semester;
        LocalDate semesterStartDate = SEMESTER_START_DATES.get(key);
        // Если дата начала семестра не найдена в карте `SEMESTER_START_DATES` — выбрасываем ошибку
        if (semesterStartDate == null) {
            String errorMessage = String.format("Дата начала для года %s / семестра %d не найдена в конфигурации SEMESTER_START_DATES.", academicYear, semester);
            log.warn(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }
        // Вычисляем начало недели: к дате начала семестра добавляем `(weekNumber - 1)` недель
        LocalDate startOfAcademicWeekCandidate = semesterStartDate.plusWeeks(weekNumber - 1);
        // Если дата начала семестра не понедельник — двигаемся назад до ближайшего понедельника
        return startOfAcademicWeekCandidate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    @Transactional(readOnly = true)
    public List<ScheduleEntryDto> getEntriesByAcademicCriteria(
            String academicYear, //  учебный год
            Integer semester, // номер семестра
            Integer weekNumber, // номер недели
            String groupNameToFilterBy, // имя группы, по которой нужно фильтровать
            boolean isAdmin) { // является ли пользователь администратором
        List<ScheduleEntry> entries;

        if (isAdmin) { // Логируем запрос от админа с указанием всех фильтров
            log.info("ADMIN REQUEST (Academic): Year={}, Sem={}, Week={}, GroupFilter={}", academicYear, semester, weekNumber, groupNameToFilterBy);
            // Админ хочет увидеть расписание только для конкретной группы
            if (groupNameToFilterBy != null && !groupNameToFilterBy.isBlank()) {
                // В зависимости от параметров вызываем метод репозитория
                // по группе, году, семестру и неделе,
                // или только по группе.
                if (academicYear != null && !academicYear.isEmpty() && semester != null && weekNumber != null) {
                    entries = repository.findByGroupNameAndAcademicYearAndSemesterAndWeekNumberOrderByStartTimeAsc(groupNameToFilterBy, academicYear, semester, weekNumber);
                } else if (academicYear != null && !academicYear.isEmpty() && semester != null) {
                    entries = repository.findByGroupNameAndAcademicYearAndSemesterOrderByStartTimeAsc(groupNameToFilterBy, academicYear, semester);
                } else if (academicYear != null && !academicYear.isEmpty()) {
                    entries = repository.findByGroupNameAndAcademicYearOrderByStartTimeAsc(groupNameToFilterBy, academicYear);
                } else {
                    entries = repository.findByGroupNameOrderByStartTimeAsc(groupNameToFilterBy);
                }
            } else { // Если админ не указал группу или фильтры — возвращаем пустой список
                if (academicYear != null && !academicYear.isEmpty() && semester != null && weekNumber != null) {
                    entries = repository.findByAcademicYearAndSemesterAndWeekNumberOrderByStartTimeAsc(academicYear, semester, weekNumber);
                } else if (academicYear != null && !academicYear.isEmpty() && semester != null) {
                    entries = repository.findByAcademicYearAndSemesterOrderByStartTimeAsc(academicYear, semester);
                } else if (academicYear != null && !academicYear.isEmpty()) {
                    entries = repository.findByAcademicYearOrderByStartTimeAsc(academicYear);
                } else {
                    log.warn("ADMIN REQUEST (Academic): Не указаны полные академические фильтры и нет фильтра по группе. Возвращается пустой список.");
                    return Collections.emptyList();
                }
            }
        } else { // Если у пользователя нет группы — возвращаем пустой список, так как он видит только своё расписание
            if (groupNameToFilterBy == null || groupNameToFilterBy.isBlank()) {
                log.warn("USER REQUEST (Academic): Для пользователя-студента не определена группа. Расписание не будет загружено.");
                return Collections.emptyList();
            }
            // Записываем в лог, какое расписание запрашивается
            log.info("USER REQUEST (Academic): Group {}, Year={}, Sem={}, Week={}", groupNameToFilterBy, academicYear, semester, weekNumber);
            // Все фильтры указаны: группа + год + семестр + неделя
            if (academicYear != null && !academicYear.isEmpty() && semester != null && weekNumber != null) {
                entries = repository.findByGroupNameAndAcademicYearAndSemesterAndWeekNumberOrderByStartTimeAsc(groupNameToFilterBy, academicYear, semester, weekNumber);
            // Указаны: группа + год + семестр
            } else if (academicYear != null && !academicYear.isEmpty() && semester != null) {
                entries = repository.findByGroupNameAndAcademicYearAndSemesterOrderByStartTimeAsc(
                        groupNameToFilterBy, academicYear, semester);
            // Указаны: группа + год
            } else if (academicYear != null && !academicYear.isEmpty()) {
                entries = repository.findByGroupNameAndAcademicYearOrderByStartTimeAsc(groupNameToFilterBy, academicYear);
            // Только группа (год, семестр, неделя не указаны)
            } else {
                entries = repository.findByGroupNameOrderByStartTimeAsc(groupNameToFilterBy);
            }
        } // Все найденные записи преобразуются в DTO с помощью `convertToDto`
        return entries.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    // Принимаем дату недели, группу и флаг `isAdmin`
    public List<ScheduleEntryDto> getEntriesForWeek(LocalDate weekStartDateInput, String groupNameToFilterBy, boolean isAdmin) {
        // Начало недели — понедельник, ближайший к входной дате
        // Конец недели — +6 дней от понедельника (полная неделя: пн–вс)
        LocalDate start = weekStartDateInput.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = start.plusDays(6);
        // Переводим начало и конец недели в `LocalDateTime` для поиска записей в БД по времени
        LocalDateTime startOfWeekDateTime = start.atStartOfDay();
        LocalDateTime endOfWeekDateTime = end.atTime(LocalTime.MAX);

        // Создаём переменную для хранения найденных записей
        List<ScheduleEntry> entries;
        // Админ может указать группу для фильтрации или получить всё расписание за неделю без группы
        if (isAdmin) {
            log.debug("ADMIN REQUEST (Calendar): Week {} - {}, GroupFilter={}", startOfWeekDateTime, endOfWeekDateTime, groupNameToFilterBy);
            if (groupNameToFilterBy != null && !groupNameToFilterBy.isBlank()) {
                entries = repository.findByGroupNameAndStartTimeBetweenOrderByStartTimeAsc(groupNameToFilterBy, startOfWeekDateTime, endOfWeekDateTime);
            } else {
                entries = repository.findByStartTimeBetweenOrderByStartTimeAsc(startOfWeekDateTime, endOfWeekDateTime);
            }
        // Обычный пользователь видит только свою группу;
        // если группа неизвестна — возвращается пустой список
        } else {
            if (groupNameToFilterBy == null || groupNameToFilterBy.isBlank()) {
                log.warn("USER REQUEST (Calendar): Для пользователя-студента не определена группа. Расписание по календарной неделе не будет загружено.");
                return Collections.emptyList();
            }
            log.debug("USER REQUEST (Calendar): Group {}, Week {} - {}", groupNameToFilterBy, startOfWeekDateTime, endOfWeekDateTime);
            entries = repository.findByGroupNameAndStartTimeBetweenOrderByStartTimeAsc(groupNameToFilterBy, startOfWeekDateTime, endOfWeekDateTime);
        }
        // Записи преобразуются в DTO (`ScheduleEntryDto`) через `convertToDto` для безопасного использования в контроллере и шаблоне
        return entries.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    // Этот метод работает в режиме только чтение , принимает id записи
    @Transactional(readOnly = true)
    public ScheduleEntryDto getEntryById(Long id) {
        // Пишем в лог, какую запись запрашиваем
        log.debug("Запрос записи расписания по ID: {}", id);
        // Ищем запись по ID.
        //Если не нашли — выбрасываем исключение EntityNotFoundException
        ScheduleEntry entry = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Запись расписания с ID " + id + " не найдена"));
        // Переводим сущность в DTO, чтобы передать в контроллер или форму
        return convertToDto(entry);
    }

    // Метод `@Transactional` изменяет данные в БД и принимает DTO с данными из формы
    @Transactional
    public ScheduleEntryDto createEntry(ScheduleEntryDto dto) {
        log.info("Создание новой записи расписания: Группа {}, Предмет {}, Тип {}", dto.getGroupName(), dto.getSubjectName(), dto.getSubjectType());
        // Превращаем DTO в сущность через `convertToEntity` и обнуляем `id`, чтобы сохранить как новую запись
        ScheduleEntry entry = convertToEntity(dto);
        entry.setId(null);
        // Сохраняем запись через репозиторий — Spring автоматически присвоит новый `ID`
        ScheduleEntry savedEntry = repository.save(entry);
        log.info("Новая запись расписания успешно создана с ID: {}", savedEntry.getId());
        return convertToDto(savedEntry);
    }

    @Transactional
    public ScheduleEntryDto updateEntry(Long id, ScheduleEntryDto dto) {
        log.info("Обновление записи расписания с ID {}: {}", id, dto);
        // Ищем запись по `ID`. Если не найдена — выбрасываем `EntityNotFoundException`
        ScheduleEntry existingEntry = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Запись расписания с ID " + id + " не найдена для обновления"));

        // Обновляем все основные поля у существующей записи данными из DTO
        existingEntry.setSubjectName(dto.getSubjectName());
        existingEntry.setTeacherName(dto.getTeacherName());
        existingEntry.setRoom(dto.getRoom());
        existingEntry.setStartTime(dto.getStartTime());
        existingEntry.setEndTime(dto.getEndTime());
        existingEntry.setAcademicYear(dto.getAcademicYear());
        existingEntry.setSemester(dto.getSemester());
        existingEntry.setWeekNumber(dto.getWeekNumber());
        existingEntry.setGroupName(dto.getGroupName());
        existingEntry.setSubjectType(dto.getSubjectType());

        // Сохраняем обновлённый объект в БД
        ScheduleEntry updatedEntry = repository.save(existingEntry);
        log.info("Запись расписания с ID {} успешно обновлена", updatedEntry.getId());
        // Возвращаем обновлённую запись в виде DTO
        return convertToDto(updatedEntry);
    }

    @Transactional
    public void deleteEntry(Long id) {
        log.info("Удаление записи расписания с ID: {}", id);
        // Проверяем наличие записи в БД — если не найдена, выбрасываем `EntityNotFoundException`
        if (!repository.existsById(id)) {
            throw new EntityNotFoundException("Запись расписания с ID " + id + " не найдена для удаления");
        }
        // Вызываем репозиторий, чтобы удалить запись по её ID
        repository.deleteById(id);
        log.info("Запись расписания с ID {} успешно удалена", id);
    }

    // Преобразует сущность `ScheduleEntry` из БД в безопасный DTO (`ScheduleEntryDto`) для использования в контроллере или HTML-странице.
    protected ScheduleEntryDto convertToDto(ScheduleEntry entity) {
        if (entity == null) return null;
        return new ScheduleEntryDto(
                entity.getId(),
                entity.getSubjectName(),
                entity.getTeacherName(),
                entity.getRoom(),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getAcademicYear(),
                entity.getSemester(),
                entity.getWeekNumber(),
                entity.getGroupName(),
                entity.getSubjectType()
        );
    }

    // Преобразует DTO (из формы) в сущность `ScheduleEntry` для сохранения в БД
    protected ScheduleEntry convertToEntity(ScheduleEntryDto dto) {
        if (dto == null) return null;
        ScheduleEntry entry = new ScheduleEntry();
        entry.setId(dto.getId());
        entry.setSubjectName(dto.getSubjectName());
        entry.setTeacherName(dto.getTeacherName());
        entry.setRoom(dto.getRoom());
        entry.setStartTime(dto.getStartTime());
        entry.setEndTime(dto.getEndTime());
        entry.setAcademicYear(dto.getAcademicYear());
        entry.setSemester(dto.getSemester());
        entry.setWeekNumber(dto.getWeekNumber());
        entry.setGroupName(dto.getGroupName());
        entry.setSubjectType(dto.getSubjectType());
        return entry;
    }
}