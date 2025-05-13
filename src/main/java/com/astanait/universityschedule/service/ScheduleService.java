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

    private final ScheduleEntryRepository scheduleEntryRepository;
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
        this.scheduleEntryRepository = scheduleEntryRepository;
    }

    public LocalDate calculateStartDateForAcademicWeek(String academicYear, int semester, int weekNumber) {
        String key = academicYear + "_" + semester;
        LocalDate semesterStartDate = SEMESTER_START_DATES.get(key);
        if (semesterStartDate == null) {
            return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }
        LocalDate startOfAcademicWeekCandidate = semesterStartDate.plusWeeks(weekNumber - 1);
        return startOfAcademicWeekCandidate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    @Transactional(readOnly = true)
    public List<ScheduleEntryDto> getEntriesByAcademicCriteria(String academicYear, Integer semester, Integer weekNumber) {
        List<ScheduleEntry> entries;
        if (academicYear != null && !academicYear.isEmpty() && semester != null && weekNumber != null) {
            entries = scheduleEntryRepository.findByAcademicYearAndSemesterAndWeekNumberOrderByStartTimeAsc(academicYear, semester, weekNumber);
        } else if (academicYear != null && !academicYear.isEmpty() && semester != null) {
            entries = scheduleEntryRepository.findByAcademicYearAndSemesterOrderByStartTimeAsc(academicYear, semester);
        } else if (academicYear != null && !academicYear.isEmpty()) {
            entries = scheduleEntryRepository.findByAcademicYearOrderByStartTimeAsc(academicYear);
        } else {
            return Collections.emptyList();
        }
        return entries.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ScheduleEntryDto> getEntriesForWeek(LocalDate weekStartDateInput) {
        LocalDate start = weekStartDateInput.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = start.plusDays(6);
        LocalDateTime startOfWeekDateTime = start.atStartOfDay();
        LocalDateTime endOfWeekDateTime = end.atTime(LocalTime.MAX);
        List<ScheduleEntry> entries = scheduleEntryRepository.findByStartTimeBetweenOrderByStartTimeAsc(startOfWeekDateTime, endOfWeekDateTime);
        return entries.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ScheduleEntryDto getEntryById(Long id) {
        ScheduleEntry entry = scheduleEntryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Запись расписания с ID " + id + " не найдена"));
        return convertToDto(entry);
    }

    @Transactional
    public ScheduleEntryDto createEntry(ScheduleEntryDto dto) {
        ScheduleEntry entry = convertToEntity(dto);
        entry.setId(null);
        ScheduleEntry savedEntry = scheduleEntryRepository.save(entry);
        return convertToDto(savedEntry);
    }

    @Transactional
    public ScheduleEntryDto updateEntry(Long id, ScheduleEntryDto dto) {
        ScheduleEntry existingEntry = scheduleEntryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Запись расписания с ID " + id + " не найдена для обновления"));

        existingEntry.setSubjectName(dto.getSubjectName());
        existingEntry.setTeacherName(dto.getTeacherName());
        existingEntry.setRoom(dto.getRoom());
        existingEntry.setStartTime(dto.getStartTime());
        existingEntry.setEndTime(dto.getEndTime());
        existingEntry.setAcademicYear(dto.getAcademicYear());
        existingEntry.setSemester(dto.getSemester());
        existingEntry.setWeekNumber(dto.getWeekNumber());

        ScheduleEntry updatedEntry = scheduleEntryRepository.save(existingEntry);
        return convertToDto(updatedEntry);
    }

    @Transactional
    public void deleteEntry(Long id) {
        if (!scheduleEntryRepository.existsById(id)) {
            throw new EntityNotFoundException("Запись расписания с ID " + id + " не найдена для удаления");
        }
        scheduleEntryRepository.deleteById(id);
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
                entity.getWeekNumber()
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
        return entry;
    }
}