package com.astanait.universityschedule.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExamScheduleEntryDto {

    private Long id;

    @NotNull(message = "Академический год обязателен")
    private Integer academicYear;

    @NotNull(message = "Академический период обязателен")
    @Min(value = 1, message = "Академический период должен быть 1 или 2")
    @Max(value = 2, message = "Академический период должен быть 1 или 2")
    private Integer academicPeriod;

    @NotNull(message = "Дата экзамена обязательна")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate examDate;

    private String dayOfWeek;

    @NotNull(message = "Время начала обязательно")
    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime startTime;

    @NotNull(message = "Время окончания обязательно")
    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    private LocalTime endTime;

    @NotBlank(message = "Название дисциплины обязательно")
    @Size(max = 255, message = "Название дисциплины не должно превышать 255 символов")
    private String disciplineName;

    @Size(max = 255, message = "Имя экзаменатора не должно превышать 255 символов")
    private String examinerName;

    @Size(max = 100, message = "Форма контроля не должна превышать 100 символов")
    private String controlForm;

    @Size(max = 100, message = "Название корпуса не должно превышать 100 символов")
    private String building;

    @Size(max = 50, message = "Номер аудитории не должен превышать 50 символов")
    private String room;

    private String additionalInfo;

    @NotBlank(message = "Группа не может быть пустой")
    @Size(max = 50, message = "Название группы не должно превышать 50 символов")
    private String groupName;

    @AssertTrue(message = "Время окончания экзамена должно быть после времени начала")
    public boolean isExamEndTimeAfterStartTime() {
        return startTime == null || endTime == null || endTime.isAfter(startTime);
    }
}