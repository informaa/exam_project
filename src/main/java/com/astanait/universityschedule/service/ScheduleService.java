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

@Service
public class ScheduleService {

    private final ScheduleEntryRepository repository;
    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);

    public static final Map<String, LocalDate> SEMESTER_START_DATES = new HashMap<>();

    static {
        SEMESTER_START_DATES.put("2024-2025_1", LocalDate.of(2024, 9, 2));
        SEMESTER_START_DATES.put("2024-2025_2", LocalDate.of(2025, 2, 3));
        SEMESTER_START_DATES.put("2025-2026_1", LocalDate.of(2025, 9, 1));
        SEMESTER_START_DATES.put("2025-2026_2", LocalDate.of(2026, 2, 2));
    }

    @Autowired
    public ScheduleService(ScheduleEntryRepository scheduleEntryRepository) {
        this.repository = scheduleEntryRepository;
    }

    public LocalDate calculateStartDateForAcademicWeek(String academicYear, int semester, int weekNumber) {
        String key = academicYear + "_" + semester;
        LocalDate semesterStartDate = SEMESTER_START_DATES.get(key);
        if (semesterStartDate == null) {
            String errorMessage = String.format("Дата начала для года %s / семестра %d не найдена в конфигурации SEMESTER_START_DATES.", academicYear, semester);
            log.warn(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }
        LocalDate startOfAcademicWeekCandidate = semesterStartDate.plusWeeks(weekNumber - 1);
        return startOfAcademicWeekCandidate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    @Transactional(readOnly = true)
    public List<ScheduleEntryDto> getEntriesByAcademicCriteria(String academicYear, Integer semester, Integer weekNumber, String userGroupName, boolean isAdmin) {
        List<ScheduleEntry> entries;
        if (isAdmin) {
            log.info("ADMIN REQUEST: Загрузка записей расписания: Год={}, Семестр={}, Неделя={}", academicYear, semester, weekNumber);
            if (academicYear != null && !academicYear.isEmpty() && semester != null && weekNumber != null) {
                entries = repository.findByAcademicYearAndSemesterAndWeekNumberOrderByStartTimeAsc(academicYear, semester, weekNumber);
            } else if (academicYear != null && !academicYear.isEmpty() && semester != null) {
                entries = repository.findByAcademicYearAndSemesterOrderByStartTimeAsc(academicYear, semester);
            } else if (academicYear != null && !academicYear.isEmpty()) {
                entries = repository.findByAcademicYearOrderByStartTimeAsc(academicYear);
            } else {
                log.warn("ADMIN REQUEST: Не указаны полные академические фильтры. Возвращается пустой список.");
                return Collections.emptyList();
            }
        } else {
            if (userGroupName == null || userGroupName.isBlank()) {
                log.warn("USER REQUEST: Для пользователя-студента не указана группа. Расписание не будет загружено.");
                return Collections.emptyList();
            }
            log.info("USER REQUEST: Загрузка записей расписания для группы {}: Год={}, Семестр={}, Неделя={}", userGroupName, academicYear, semester, weekNumber);
            if (academicYear != null && !academicYear.isEmpty() && semester != null && weekNumber != null) {
                entries = repository.findByGroupNameAndAcademicYearAndSemesterAndWeekNumberOrderByStartTimeAsc(userGroupName, academicYear, semester, weekNumber);
            } else if (academicYear != null && !academicYear.isEmpty() && semester != null) {
                entries = repository.findByGroupNameAndAcademicYearAndSemesterOrderByStartTimeAsc(userGroupName, academicYear, semester);
            } else if (academicYear != null && !academicYear.isEmpty()) {
                entries = repository.findByGroupNameAndAcademicYearOrderByStartTimeAsc(userGroupName, academicYear);
            } else {
                entries = repository.findByGroupNameOrderByStartTimeAsc(userGroupName);
            }
        }
        return entries.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ScheduleEntryDto> getEntriesForWeek(LocalDate weekStartDateInput, String userGroupName, boolean isAdmin) {
        LocalDate start = weekStartDateInput.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = start.plusDays(6);
        LocalDateTime startOfWeekDateTime = start.atStartOfDay();
        LocalDateTime endOfWeekDateTime = end.atTime(LocalTime.MAX);
        List<ScheduleEntry> entries;
        if (isAdmin) {
            log.debug("ADMIN REQUEST: Загрузка записей расписания для календарной недели: {} - {}", startOfWeekDateTime, endOfWeekDateTime);
            entries = repository.findByStartTimeBetweenOrderByStartTimeAsc(startOfWeekDateTime, endOfWeekDateTime);
        } else {
            if (userGroupName == null || userGroupName.isBlank()) {
                log.warn("USER REQUEST: Для пользователя-студента не указана группа. Расписание по календарной неделе не будет загружено.");
                return Collections.emptyList();
            }
            log.debug("USER REQUEST: Загрузка записей расписания для группы {} для календарной недели: {} - {}", userGroupName, startOfWeekDateTime, endOfWeekDateTime);
            entries = repository.findByGroupNameAndStartTimeBetweenOrderByStartTimeAsc(userGroupName, startOfWeekDateTime, endOfWeekDateTime);
        }
        return entries.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ScheduleEntryDto getEntryById(Long id) {
        log.debug("Запрос записи расписания по ID: {}", id);
        ScheduleEntry entry = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Запись расписания с ID " + id + " не найдена"));
        return convertToDto(entry);
    }

    @Transactional
    public ScheduleEntryDto createEntry(ScheduleEntryDto dto) {
        log.info("Создание новой записи расписания: Группа {}, Предмет {}, Тип {}", dto.getGroupName(), dto.getSubjectName(), dto.getSubjectType());
        ScheduleEntry entry = convertToEntity(dto);
        entry.setId(null);
        ScheduleEntry savedEntry = repository.save(entry);
        log.info("Новая запись расписания успешно создана с ID: {}", savedEntry.getId());
        return convertToDto(savedEntry);
    }

    @Transactional
    public ScheduleEntryDto updateEntry(Long id, ScheduleEntryDto dto) {
        log.info("Обновление записи расписания с ID {}: {}", id, dto);
        ScheduleEntry existingEntry = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Запись расписания с ID " + id + " не найдена для обновления"));
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
        ScheduleEntry updatedEntry = repository.save(existingEntry);
        log.info("Запись расписания с ID {} успешно обновлена", updatedEntry.getId());
        return convertToDto(updatedEntry);
    }

    @Transactional
    public void deleteEntry(Long id) {
        log.info("Удаление записи расписания с ID: {}", id);
        if (!repository.existsById(id)) {
            throw new EntityNotFoundException("Запись расписания с ID " + id + " не найдена для удаления");
        }
        repository.deleteById(id);
        log.info("Запись расписания с ID {} успешно удалена", id);
    }

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