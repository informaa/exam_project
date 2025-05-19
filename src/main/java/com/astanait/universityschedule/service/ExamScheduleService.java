package com.astanait.universityschedule.service;

import com.astanait.universityschedule.dto.ExamScheduleEntryDto;
import com.astanait.universityschedule.model.ExamScheduleEntry;
import com.astanait.universityschedule.repository.ExamScheduleEntryRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class ExamScheduleService {

    private final ExamScheduleEntryRepository examRepository;
    private static final Logger log = LoggerFactory.getLogger(ExamScheduleService.class);

    @Autowired
    public ExamScheduleService(ExamScheduleEntryRepository examRepository) {
        this.examRepository = examRepository;
    }

    protected ExamScheduleEntryDto convertToDto(ExamScheduleEntry entity) {
        if (entity == null) return null;
        return new ExamScheduleEntryDto(
                entity.getId(),
                entity.getAcademicYear(),
                entity.getAcademicPeriod(),
                entity.getExamDate(),
                entity.getDayOfWeek() != null ? entity.getDayOfWeek() : (entity.getExamDate() != null ? entity.getExamDate().getDayOfWeek().getDisplayName(TextStyle.FULL, new Locale("ru")) : null),
                entity.getStartTime(),
                entity.getEndTime(),
                entity.getDisciplineName(),
                entity.getExaminerName(),
                entity.getControlForm(),
                entity.getBuilding(),
                entity.getRoom(),
                entity.getAdditionalInfo(),
                entity.getGroupName()
        );
    }

    protected ExamScheduleEntry convertToEntity(ExamScheduleEntryDto dto) {
        if (dto == null) return null;
        ExamScheduleEntry entity = new ExamScheduleEntry();
        entity.setId(dto.getId());
        entity.setAcademicYear(dto.getAcademicYear());
        entity.setAcademicPeriod(dto.getAcademicPeriod());
        entity.setExamDate(dto.getExamDate());
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());
        entity.setDisciplineName(dto.getDisciplineName());
        entity.setExaminerName(dto.getExaminerName());
        entity.setControlForm(dto.getControlForm());
        entity.setBuilding(dto.getBuilding());
        entity.setRoom(dto.getRoom());
        entity.setAdditionalInfo(dto.getAdditionalInfo());
        entity.setGroupName(dto.getGroupName());
        return entity;
    }

    @Transactional(readOnly = true)
    public List<ExamScheduleEntryDto> getExamEntries(Integer academicYear, Integer academicPeriod, String userGroupName, boolean isAdmin) {
        List<ExamScheduleEntry> entries;

        if (isAdmin) {
            log.info("ADMIN REQUEST: Загрузка записей экзаменов: Год={}, Период={}", academicYear, academicPeriod);
            if (academicYear != null && academicPeriod != null) {
                entries = examRepository.findByAcademicYearAndAcademicPeriodOrderByExamDateAscStartTimeAsc(academicYear, academicPeriod);
            } else if (academicYear != null) {
                entries = examRepository.findByAcademicYearOrderByExamDateAscStartTimeAsc(academicYear);
            } else {
                log.warn("ADMIN REQUEST: Год и/или период не указаны. Загрузка ВСЕХ записей экзаменов.");
                entries = examRepository.findAll();
            }
        } else {
            if (userGroupName == null || userGroupName.isBlank()) {
                log.warn("USER REQUEST: Для пользователя-студента не определена группа. Расписание экзаменов не будет загружено.");
                return Collections.emptyList();
            }
            log.info("USER REQUEST: Загрузка записей экзаменов для группы {}: Год={}, Период={}", userGroupName, academicYear, academicPeriod);
            if (academicYear != null && academicPeriod != null) {
                entries = examRepository.findByGroupNameAndAcademicYearAndAcademicPeriodOrderByExamDateAscStartTimeAsc(userGroupName, academicYear, academicPeriod);
            } else if (academicYear != null) {
                entries = examRepository.findByGroupNameAndAcademicYearOrderByExamDateAscStartTimeAsc(userGroupName, academicYear);
            } else {
                entries = examRepository.findByGroupNameOrderByExamDateAscStartTimeAsc(userGroupName);
            }
        }
        return entries.stream().map(this::convertToDto).collect(Collectors.toList());
    }
}