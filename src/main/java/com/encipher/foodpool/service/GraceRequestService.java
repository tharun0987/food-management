package com.encipher.foodpool.service;

import com.encipher.foodpool.model.*;
import com.encipher.foodpool.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GraceRequestService {
    
    private final GraceRequestRepository graceRequestRepository;
    private final FoodPoolRepository foodPoolRepository;
    private final MenuConfigRepository menuConfigRepository;
    private final AuditService auditService;
    private final CliqNotificationService cliqNotificationService;
    
    /**
     * Check if grace period is currently active for a food date
     */
    public boolean isWithinGracePeriod(LocalDate foodDate) {
        // Find the menu config for the survey date (day before food date typically)
        // Grace period starts when pool closes and lasts for configured hours
        Optional<MenuConfig> configOpt = menuConfigRepository.findFirstByFoodDateOrderByDateDesc(foodDate);
        
        if (configOpt.isEmpty()) {
            return false;
        }
        
        MenuConfig config = configOpt.get();
        
        // Pool must be closed
        if (config.isPoolOpen()) {
            return false;
        }
        
        // Check if within grace period
        if (config.getGraceEndTime() == null) {
            return false;
        }
        
        return LocalDateTime.now().isBefore(config.getGraceEndTime());
    }
    
    /**
     * Get the current survey's food date if in grace period
     */
    public Optional<LocalDate> getGracePeriodFoodDate() {
        LocalDate today = LocalDate.now();
        // Check today and tomorrow as potential food dates
        if (isWithinGracePeriod(today)) {
            return Optional.of(today);
        }
        if (isWithinGracePeriod(today.plusDays(1))) {
            return Optional.of(today.plusDays(1));
        }
        return Optional.empty();
    }
    
    /**
     * Submit a grace request (late vote request)
     */
    public GraceRequest submitGraceRequest(Employee employee, LocalDate foodDate, 
                                            String foodType, String reason) throws Exception {
        // Check if within grace period
        if (!isWithinGracePeriod(foodDate)) {
            throw new Exception("Grace period has ended or is not active for this food date");
        }
        
        // Check if already has a pool entry
        if (foodPoolRepository.findByEmployeeIdAndFoodDate(employee.getEmployeeId(), foodDate).isPresent()) {
            throw new Exception("You have already voted for this food date");
        }
        
        // Check if already has a pending/approved grace request
        Optional<GraceRequest> existingRequest = graceRequestRepository
                .findByEmployeeIdAndFoodDate(employee.getEmployeeId(), foodDate);
        
        if (existingRequest.isPresent()) {
            GraceRequest existing = existingRequest.get();
            if (existing.isPending()) {
                throw new Exception("You already have a pending grace request for this date");
            }
            if (existing.isApproved()) {
                throw new Exception("Your grace request has already been approved");
            }
            // If rejected, allow resubmission
        }
        
        GraceRequest request = GraceRequest.builder()
                .employeeId(employee.getEmployeeId())
                .employeeName(employee.getName())
                .employeeEmail(employee.getEmail())
                .foodDate(foodDate)
                .foodType(foodType)
                .reason(reason)
                .requestedAt(LocalDateTime.now())
                .status(GraceRequest.STATUS_PENDING)
                .build();
        
        GraceRequest saved = graceRequestRepository.save(request);
        
        log.info("Grace request submitted by {} for food date {}", employee.getName(), foodDate);
        
        // TODO: Notify admins about new grace request
        
        return saved;
    }
    
    /**
     * Approve a grace request
     */
    public GraceRequest approveRequest(String requestId, String adminEmail, String adminName) throws Exception {
        GraceRequest request = graceRequestRepository.findById(requestId)
                .orElseThrow(() -> new Exception("Grace request not found"));
        
        if (!request.isPending()) {
            throw new Exception("Request is not pending. Current status: " + request.getStatus());
        }
        
        // Check if the food date is not in the past
        if (request.getFoodDate().isBefore(LocalDate.now())) {
            throw new Exception("Cannot approve request for a past date");
        }
        
        // Check if employee already has a pool entry now (might have been created while pending)
        if (foodPoolRepository.findByEmployeeIdAndFoodDate(request.getEmployeeId(), request.getFoodDate()).isPresent()) {
            throw new Exception("Employee already has a vote for this date");
        }
        
        // Update request status
        request.setStatus(GraceRequest.STATUS_APPROVED);
        request.setReviewedBy(adminEmail);
        request.setReviewedByName(adminName);
        request.setReviewedAt(LocalDateTime.now());
        
        GraceRequest saved = graceRequestRepository.save(request);
        
        // Create the actual pool entry
        FoodPool pool = new FoodPool(
                request.getEmployeeId(),
                request.getEmployeeName(),
                request.getEmployeeEmail(),
                request.getFoodType(),
                LocalDate.now()  // Survey date is today (grace period)
        );
        pool.setFoodDate(request.getFoodDate());
        foodPoolRepository.save(pool);
        
        // Audit log
        auditService.logGraceRequestApproved(adminEmail, adminName, 
                request.getEmployeeId(), request.getEmployeeName());
        
        log.info("Grace request {} approved by {} for employee {}", 
                requestId, adminName, request.getEmployeeName());
        
        // TODO: Notify employee about approval
        
        return saved;
    }
    
    /**
     * Reject a grace request
     */
    public GraceRequest rejectRequest(String requestId, String adminEmail, String adminName, 
                                       String rejectionReason) throws Exception {
        GraceRequest request = graceRequestRepository.findById(requestId)
                .orElseThrow(() -> new Exception("Grace request not found"));
        
        if (!request.isPending()) {
            throw new Exception("Request is not pending. Current status: " + request.getStatus());
        }
        
        request.setStatus(GraceRequest.STATUS_REJECTED);
        request.setReviewedBy(adminEmail);
        request.setReviewedByName(adminName);
        request.setReviewedAt(LocalDateTime.now());
        request.setRejectionReason(rejectionReason);
        
        GraceRequest saved = graceRequestRepository.save(request);
        
        // Audit log
        auditService.logGraceRequestRejected(adminEmail, adminName, 
                request.getEmployeeId(), request.getEmployeeName(), rejectionReason);
        
        log.info("Grace request {} rejected by {} for employee {}. Reason: {}", 
                requestId, adminName, request.getEmployeeName(), rejectionReason);
        
        // TODO: Notify employee about rejection
        
        return saved;
    }
    
    /**
     * Get all pending grace requests
     */
    public List<GraceRequest> getPendingRequests() {
        return graceRequestRepository.findByStatusOrderByRequestedAtDesc(GraceRequest.STATUS_PENDING);
    }
    
    /**
     * Get pending requests for a specific food date
     */
    public List<GraceRequest> getPendingRequestsForDate(LocalDate foodDate) {
        return graceRequestRepository.findByStatusAndFoodDateOrderByRequestedAtDesc(
                GraceRequest.STATUS_PENDING, foodDate);
    }
    
    /**
     * Get all requests for a food date
     */
    public List<GraceRequest> getRequestsForDate(LocalDate foodDate) {
        return graceRequestRepository.findByFoodDateOrderByRequestedAtDesc(foodDate);
    }
    
    /**
     * Get requests by employee
     */
    public List<GraceRequest> getRequestsByEmployee(String employeeId) {
        return graceRequestRepository.findByEmployeeIdOrderByRequestedAtDesc(employeeId);
    }
    
    /**
     * Get request by ID
     */
    public Optional<GraceRequest> getRequestById(String id) {
        return graceRequestRepository.findById(id);
    }
    
    /**
     * Get count of pending requests
     */
    public long getPendingCount() {
        return graceRequestRepository.countByStatus(GraceRequest.STATUS_PENDING);
    }
    
    /**
     * Check if employee can submit grace request for a food date
     */
    public boolean canSubmitGraceRequest(String employeeId, LocalDate foodDate) {
        // Must be within grace period
        if (!isWithinGracePeriod(foodDate)) {
            return false;
        }
        
        // Must not have already voted
        if (foodPoolRepository.findByEmployeeIdAndFoodDate(employeeId, foodDate).isPresent()) {
            return false;
        }
        
        // Must not have a pending request
        Optional<GraceRequest> existingRequest = graceRequestRepository
                .findByEmployeeIdAndFoodDate(employeeId, foodDate);
        
        if (existingRequest.isPresent() && existingRequest.get().isPending()) {
            return false;
        }
        
        return true;
    }
}

