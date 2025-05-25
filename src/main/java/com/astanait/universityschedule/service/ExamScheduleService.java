package com.astanait.universityschedule.service;

import com.astanait.universityschedule.dto.ExamScheduleEntryDto;
import com.astanait.universityschedule.model.ExamScheduleEntry;
import com.astanait.universityschedule.repository.ExamScheduleEntryRepository;
import jakarta.persistence.EntityNotFoundException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

// Это Spring-сервис (`@Service`), доступный через внедрение зависимостей (`@Autowired`)
@Service
public class ExamScheduleService {

    // подключение к базе данных
    private final ExamScheduleEntryRepository examRepository;
    // форматтеры дат и времени для отображения
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    //  для записи логов
    private static final Logger log = LoggerFactory.getLogger(ExamScheduleService.class);

    // Spring внедряет репозиторий в конструктор — стандартный способ управления зависимостями
    @Autowired
    public ExamScheduleService(ExamScheduleEntryRepository examRepository) {
        this.examRepository = examRepository;
    }

    // Преобразует сущность из БД в DTO для безопасного использования
    protected ExamScheduleEntryDto convertToDto(ExamScheduleEntry entity) {
        // Если сущность пустая — возвращаем `null`, чтобы избежать ошибок
        if (entity == null) return null;

        // Создаём DTO ExamScheduleEntryDto на основе сущности:
        // Берём день недели из сущности, если он задан.
        // Если день не указан, но есть дата экзамена (examDate) — вычисляем день недели из даты.
        // Переводим день недели на русский (например, MONDAY → "Понедельник").
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

    // Объявляем метод, принимающий `ExamScheduleEntryDto` и возвращающий сущность `ExamScheduleEntry`
    protected ExamScheduleEntry convertToEntity(ExamScheduleEntryDto dto) {
        // Если `DTO` пустой (`null`) — возвращаем `null`, чтобы избежать ошибок
        if (dto == null) return null;
        // Создаём новую сущность, которую будем заполнять данными из DTO
        ExamScheduleEntry entity = new ExamScheduleEntry();
        // Передаём данные из DTO в сущность
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
        // Возвращаем заполненную сущность, готовую для сохранения в БД через репозиторий
        return entity;
    }

    // Метод работает в режиме чтения — важно для производительности и безопасности
    @Transactional(readOnly = true)
    // Получаем список экзаменов в виде DTO (`ExamScheduleEntryDto`) для отображения на странице
    public List<ExamScheduleEntryDto> getExamEntries(Integer academicYear, Integer academicPeriod, String userGroupName, boolean isAdmin) {
        List<ExamScheduleEntry> entries;
        if (isAdmin) {
            // Админ видит все группы, фильтрует по группе, году и периоду
            // Если группа указана — возвращаем только её экзамены
            log.info("ADMIN REQUEST: Loading exam entries: Year={}, Period={}", academicYear, academicPeriod);
            if (userGroupName != null && !userGroupName.isBlank()) {
                if (academicYear != null && academicPeriod != null) {
                    entries = examRepository.findByGroupNameAndAcademicYearAndAcademicPeriodOrderByExamDateAscStartTimeAsc(userGroupName, academicYear, academicPeriod);
                } else if (academicYear != null) {
                    entries = examRepository.findByGroupNameAndAcademicYearOrderByExamDateAscStartTimeAsc(userGroupName, academicYear);
                } else {
                    entries = examRepository.findByGroupNameOrderByExamDateAscStartTimeAsc(userGroupName);
                }

            } else { // Если группа не указана — загружаем все экзамены по фильтрам или без них
                if (academicYear != null && academicPeriod != null) {
                    entries = examRepository.findByAcademicYearAndAcademicPeriodOrderByExamDateAscStartTimeAsc(academicYear, academicPeriod);
                } else if (academicYear != null) {
                    entries = examRepository.findByAcademicYearOrderByExamDateAscStartTimeAsc(academicYear);
                } else {
                    log.warn("ADMIN REQUEST: Year and/or period not specified for exams. Loading ALL exam entries.");
                    entries = examRepository.findAll();
                }
            }
        } else { // Если у пользователя нет группы — возвращаем пустой список
            if (userGroupName == null || userGroupName.isBlank()) {
                log.warn("USER REQUEST: Group name not defined for student user. No exam schedule will be loaded.");
                return Collections.emptyList();
            }
            log.info("USER REQUEST: Loading exam entries for group {}: Year={}, Period={}", userGroupName, academicYear, academicPeriod);

            // Загружаем экзамены только для его группы с фильтрами по году и периоду
            if (academicYear != null && academicPeriod != null) {
                entries = examRepository.findByGroupNameAndAcademicYearAndAcademicPeriodOrderByExamDateAscStartTimeAsc(userGroupName, academicYear, academicPeriod);
            } else if (academicYear != null) {
                entries = examRepository.findByGroupNameAndAcademicYearOrderByExamDateAscStartTimeAsc(userGroupName, academicYear);
            } else {
                entries = examRepository.findByGroupNameOrderByExamDateAscStartTimeAsc(userGroupName);
            }
        }
        // Преобразуем записи в DTO через `convertToDto` для передачи в контроллер и отображения
        return entries.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    // Метод чтения по `id`, возвращает DTO записи об экзамене
    @Transactional(readOnly = true)
    // Ищем запись по `id`. Если не найдена — ошибка `EntityNotFoundException`
    public ExamScheduleEntryDto getExamById(Long id) {
        log.debug("Requesting exam entry by ID: {}", id);
        ExamScheduleEntry entry = examRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Exam entry with ID " + id + " not found"));
        // Преобразуем сущность в DTO и возвращаем его
        return convertToDto(entry);
    }

    // Метод изменяет данные в БД — используется `@Transactional` без `readOnly`
    @Transactional
    public ExamScheduleEntryDto saveOrUpdateExamEntry(ExamScheduleEntryDto dto) {
        // Преобразуем DTO в сущность, которую можно сохранить в БД
        ExamScheduleEntry entry = convertToEntity(dto);
        // Если есть дата экзамена — вычисляем и переводим день недели на русский
        if (entry.getExamDate() != null) {
            entry.setDayOfWeek(entry.getExamDate().getDayOfWeek().getDisplayName(TextStyle.FULL, new Locale("ru")));
        } else {
            entry.setDayOfWeek(null);
        }
        // Если запись с таким `id` не найдена в БД — выбрасываем ошибку
        if (entry.getId() != null && !examRepository.existsById(entry.getId())) {
            log.warn("Attempt to update non-existent exam entry with ID {}", entry.getId());
            throw new EntityNotFoundException("Attempt to update non-existent exam entry with ID " + entry.getId());

            // Логируем, является ли запись новой или обновляем существующую
        } else if (entry.getId() == null) {
            log.info("Creating new exam entry for group {}: {}", dto.getGroupName(), dto.getDisciplineName());
        } else {
            log.info("Updating exam entry with ID: {}. Group {}", entry.getId(), dto.getGroupName());
        }
        // Сохраняем или обновляем запись в БД
        ExamScheduleEntry savedEntry = examRepository.save(entry);
        // Преобразуем сохранённую сущность в DTO и возвращаем её
        return convertToDto(savedEntry);
    }

    @Transactional
    public void deleteExamEntry(Long id) { // Метод принимает один параметр — id удаляемой записи
        // Пишем в лог, что начали удаление записи с таким-то ID
        log.info("Deleting exam entry with ID: {}", id);

        // Сначала проверяем, существует ли запись в БД:
        // если нет — логируем и выбрасываем `EntityNotFoundException`
        if (!examRepository.existsById(id)) {
            log.warn("Exam entry with ID {} not found for deletion", id);
            throw new EntityNotFoundException("Exam entry with ID " + id + " not found for deletion");
        }
        // Вызываем репозиторий, чтобы удалить запись по её ID
        examRepository.deleteById(id);
        log.info("Deleted exam entry with ID: {}", id);
    }

    // Метод создаёт поток байтов с Excel-файлом из списка записей. Может выбросить `IOException`
    public ByteArrayInputStream exportExamsToExcel(List<ExamScheduleEntryDto> exams) throws IOException {
        // Задаём заголовки столбцов таблицы
        String[] columns = {"Дата", "День недели", "Время", "Дисциплина", "Группа", "Экзаменатор", "Форма контроля", "Корпус", "Аудитория", "Доп. инфо"};
        try (Workbook workbook = new XSSFWorkbook(); // это Excel-файл формата .xlsx
             ByteArrayOutputStream out = new ByteArrayOutputStream();) {
            // страница внутри файла с названием "Расписание экзаменов"
            Sheet sheet = workbook.createSheet("Расписание экзаменов");

            // Создаём первую строку Excel-файла — заголовки столбцов
            Row headerRow = sheet.createRow(0);
            for (int col = 0; col < columns.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(columns[col]);
            }

            // Для каждого экзамена создаём строку в Excel:
            // дата в формате `dd.MM.yyyy`,
            // день недели из DTO или вычисленный из даты на русском,
            // время: объединяем начало и окончание,
            // остальные данные копируются: предмет, группа, преподаватель и т.д.
            int rowIdx = 1;
            if (exams != null) {
                for (ExamScheduleEntryDto exam : exams) {
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(
                            exam.getExamDate() != null ? exam.getExamDate().format(dateFormatter) : "");
                    row.createCell(1).setCellValue(
                            exam.getDayOfWeek() != null ? exam.getDayOfWeek() :
                                    (exam.getExamDate() != null ? exam.getExamDate().getDayOfWeek().getDisplayName(TextStyle.FULL, new Locale("ru")) : ""));
                    row.createCell(2).setCellValue(
                            (exam.getStartTime() != null ? exam.getStartTime().format(timeFormatter) : "") + " - " +
                                    (exam.getEndTime() != null ? exam.getEndTime().format(timeFormatter) : ""));
                    row.createCell(3).setCellValue(exam.getDisciplineName());
                    row.createCell(4).setCellValue(exam.getGroupName());
                    row.createCell(5).setCellValue(exam.getExaminerName());
                    row.createCell(6).setCellValue(exam.getControlForm());
                    row.createCell(7).setCellValue(exam.getBuilding());
                    row.createCell(8).setCellValue(exam.getRoom());
                    row.createCell(9).setCellValue(exam.getAdditionalInfo());
                }
            }
            // Автоподбор ширины столбцов для отображения всего текста в ячейках
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }
            // Записываем данные Excel в байтовый поток и возвращаем как `ByteArrayInputStream`
            workbook.write(out);
            log.info("Excel file generated successfully for {} exam entries.", exams != null ? exams.size() : 0);
            return new ByteArrayInputStream(out.toByteArray());

        // Если ошибка при создании файла — пишем в лог и пробрасываем исключение.
        } catch (IOException e) {
            log.error("Error generating Excel file for exams: {}", e.getMessage(), e);
            throw e;
        }
    }
}