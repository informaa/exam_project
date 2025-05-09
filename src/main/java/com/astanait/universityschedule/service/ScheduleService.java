package com.astanait.universityschedule.service;

import com.astanait.universityschedule.dto.ScheduleEntryDto;
import com.astanait.universityschedule.model.ScheduleEntry;
import com.astanait.universityschedule.repository.ScheduleEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ScheduleService {

    private final ScheduleEntryRepository scheduleEntryRepository;
    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);

    @Autowired
    public ScheduleService(ScheduleEntryRepository scheduleEntryRepository) {
        this.scheduleEntryRepository = scheduleEntryRepository;
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
        return new ScheduleEntry(
                dto.getId(),
                dto.getSubjectName(),
                dto.getTeacherName(),
                dto.getRoom(),
                dto.getStartTime(),
                dto.getEndTime(),
                dto.getAcademicYear(),
                dto.getSemester(),
                dto.getWeekNumber()
        );
    }

    @Transactional(readOnly = true)
    public List<ScheduleEntryDto> getAllScheduleEntries() {
        log.debug("Запрос всех записей расписания");
        List<ScheduleEntry> entries = scheduleEntryRepository.findAll();
        return entries.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
}