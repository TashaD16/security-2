package com.example.moduleA.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeclarationDto {
    private String id;
    
    @NotBlank(message = "Number is required")
    private String number;
    
    @NotBlank(message = "Type is required")
    private String type;
    
    @NotNull(message = "Date is required")
    private LocalDateTime date;
    
    private String description;
}
