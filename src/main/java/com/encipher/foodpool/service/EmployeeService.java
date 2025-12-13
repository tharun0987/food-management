package com.encipher.foodpool.service;

import com.encipher.foodpool.model.Employee;
import com.encipher.foodpool.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeService {
    
    private final EmployeeRepository employeeRepository;
    
    public Optional<Employee> findByEmail(String email) {
        return employeeRepository.findByEmailIgnoreCase(email);
    }
    
    public Optional<Employee> findByEmployeeId(String employeeId) {
        return employeeRepository.findByEmployeeId(employeeId);
    }
    
    public Optional<Employee> findByName(String name) {
        return employeeRepository.findByNameContainingIgnoreCase(name);
    }
    
    public Employee findOrMatch(String zohoName, String zohoEmail) {
        // Try email first
        Optional<Employee> byEmail = findByEmail(zohoEmail);
        if (byEmail.isPresent()) {
            return byEmail.get();
        }
        
        // Try exact name match
        List<Employee> allEmployees = employeeRepository.findByIsActiveTrue();
        
        for (Employee emp : allEmployees) {
            // Exact match (case insensitive)
            if (emp.getName().equalsIgnoreCase(zohoName)) {
                return emp;
            }
        }
        
        // Try partial name match
        String[] nameParts = zohoName.toLowerCase().split("\\s+");
        for (Employee emp : allEmployees) {
            String empNameLower = emp.getName().toLowerCase();
            boolean match = Arrays.stream(nameParts)
                    .filter(p -> p.length() > 2)  // Skip short words
                    .anyMatch(empNameLower::contains);
            if (match) {
                return emp;
            }
        }
        
        return null;
    }
    
    public List<Employee> getAllActiveEmployees() {
        return employeeRepository.findByIsActiveTrue();
    }
    
    public List<Employee> getAdmins() {
        // Get both legacy admins and role-based admins
        List<Employee> admins = new ArrayList<>();
        admins.addAll(employeeRepository.findByIsAdminTrue());
        admins.addAll(employeeRepository.findByRole("ADMINISTRATOR"));
        admins.addAll(employeeRepository.findByRole("CONTRIBUTOR"));
        
        // Remove duplicates
        return admins.stream()
                .filter(e -> e != null)
                .distinct()
                .toList();
    }
    
    public List<Employee> getAdministrators() {
        List<Employee> result = new ArrayList<>();
        result.addAll(employeeRepository.findByRole("ADMINISTRATOR"));
        // Include legacy admins
        result.addAll(employeeRepository.findByIsAdminTrue());
        return result.stream().distinct().toList();
    }
    
    public List<Employee> getContributors() {
        return employeeRepository.findByRole("CONTRIBUTOR");
    }
    
    public boolean isAdmin(String email) {
        if (email == null) return false;
        Optional<Employee> emp = findByEmail(email);
        return emp.map(Employee::hasAdminAccess).orElse(false);
    }
    
    public boolean isAdministrator(String email) {
        if (email == null) return false;
        Optional<Employee> emp = findByEmail(email);
        return emp.map(Employee::isAdministrator).orElse(false);
    }
    
    public boolean isAdminByName(String name) {
        if (name == null) return false;
        List<Employee> admins = getAdmins();
        return admins.stream()
                .anyMatch(a -> a.getName().equalsIgnoreCase(name));
    }
    
    public Employee save(Employee employee) {
        return employeeRepository.save(employee);
    }
    
    public void setRole(String employeeId, String role) {
        employeeRepository.findByEmployeeId(employeeId).ifPresent(emp -> {
            emp.setRole(role);
            // Sync legacy field
            emp.setAdmin("ADMINISTRATOR".equals(role) || "CONTRIBUTOR".equals(role));
            employeeRepository.save(emp);
            log.info("Set role={} for employee: {} - {}", role, employeeId, emp.getName());
        });
    }
    
    public void setAdmin(String employeeId, boolean isAdmin) {
        employeeRepository.findByEmployeeId(employeeId).ifPresent(emp -> {
            emp.setAdmin(isAdmin);
            if (isAdmin && (emp.getRole() == null || "USER".equals(emp.getRole()))) {
                emp.setRole("ADMINISTRATOR");
            } else if (!isAdmin) {
                emp.setRole("USER");
            }
            employeeRepository.save(emp);
            log.info("Set admin={} for employee: {} - {}", isAdmin, employeeId, emp.getName());
        });
    }
    
    public void setActiveStatus(String employeeId, boolean isActive) {
        employeeRepository.findByEmployeeId(employeeId).ifPresent(emp -> {
            emp.setActive(isActive);
            employeeRepository.save(emp);
            log.info("Set active={} for employee: {} - {}", isActive, employeeId, emp.getName());
        });
    }
    
    public void deleteEmployee(String employeeId) {
        employeeRepository.findByEmployeeId(employeeId).ifPresent(emp -> {
            employeeRepository.delete(emp);
            log.info("Deleted employee: {} - {}", employeeId, emp.getName());
        });
    }
    
    public Employee addEmployee(String employeeId, String name, String email, boolean isAdmin) {
        // Check if exists
        if (employeeRepository.findByEmployeeId(employeeId).isPresent()) {
            throw new RuntimeException("Employee ID already exists: " + employeeId);
        }
        
        Employee employee = new Employee(employeeId, name, email, LocalDate.now());
        employee.setAdmin(isAdmin);
        employee.setRole(isAdmin ? "ADMINISTRATOR" : "USER");
        employee.setActive(true);
        
        return employeeRepository.save(employee);
    }
    
    public Employee addEmployeeWithRole(String employeeId, String name, String email, String role) {
        // Check if exists
        if (employeeRepository.findByEmployeeId(employeeId).isPresent()) {
            throw new RuntimeException("Employee ID already exists: " + employeeId);
        }
        
        Employee employee = new Employee(employeeId, name, email, LocalDate.now());
        employee.setRole(role);
        employee.setAdmin("ADMINISTRATOR".equals(role) || "CONTRIBUTOR".equals(role));
        employee.setActive(true);
        
        return employeeRepository.save(employee);
    }
    
    /**
     * Load employees from Excel file into MongoDB
     */
    public int loadFromExcel(InputStream inputStream) {
        int count = 0;
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            
            for (int i = 2; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                Cell idCell = row.getCell(1);
                Cell nameCell = row.getCell(2);
                Cell dojCell = row.getCell(3);
                
                if (idCell == null || nameCell == null) continue;
                
                String employeeId = getCellValue(idCell);
                String name = getCellValue(nameCell);
                
                if (employeeId.isEmpty() || name.isEmpty()) continue;
                
                if (employeeRepository.findByEmployeeId(employeeId).isPresent()) {
                    continue;
                }
                
                LocalDate doj = null;
                if (dojCell != null && dojCell.getCellType() == CellType.NUMERIC 
                        && DateUtil.isCellDateFormatted(dojCell)) {
                    doj = dojCell.getDateCellValue().toInstant()
                            .atZone(ZoneId.systemDefault()).toLocalDate();
                }
                
                Employee employee = new Employee(employeeId, name, null, doj);
                employee.setRole("USER");
                employeeRepository.save(employee);
                count++;
                log.info("Loaded employee: {} - {}", employeeId, name);
            }
        } catch (Exception e) {
            log.error("Error loading Excel: {}", e.getMessage());
        }
        return count;
    }
    
    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default -> "";
        };
    }
}
