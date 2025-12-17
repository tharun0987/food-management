package com.encipher.foodpool.controller;

import com.encipher.foodpool.model.*;
import com.encipher.foodpool.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Controller
@RequiredArgsConstructor
@Slf4j
public class MainController {
    
    private final EmployeeService employeeService;
    private final FoodPoolService foodPoolService;
    
    @GetMapping("/")
    public String login() {
        return "login";
    }
    
    @GetMapping("/home")
    public String home(@AuthenticationPrincipal OAuth2User user, Model model) {
        if (user == null) {
            return "redirect:/";
        }
        
        Boolean isRegistered = user.getAttribute("isRegistered");
        if (isRegistered == null || !isRegistered) {
            model.addAttribute("error", "You are not registered as an employee. Please contact HR.");
            return "error";
        }
        
        String employeeId = user.getAttribute("employeeId");
        String employeeName = user.getAttribute("employeeName");
        Boolean isAdmin = user.getAttribute("isAdmin");
        
        model.addAttribute("employeeId", employeeId);
        model.addAttribute("employeeName", employeeName);
        model.addAttribute("isAdmin", isAdmin != null && isAdmin);
        model.addAttribute("email", user.getAttribute("Email"));
        
        LocalDate today = LocalDate.now();
        
        // Today's menu status (for voting)
        MenuConfig menu = foodPoolService.getTodayMenu();
        model.addAttribute("foodAvailable", menu.isFoodAvailable());
        model.addAttribute("poolOpen", menu.isPoolOpen());
        
        // Food date from survey
        LocalDate foodDate = menu.getFoodDate() != null ? menu.getFoodDate() : today.plusDays(1);
        model.addAttribute("foodDate", foodDate);
        model.addAttribute("foodDateFormatted", foodDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d")));
        
        // Check if today is a food collection day
        boolean isFoodCollectionDay = foodPoolService.isFoodCollectionDay();
        model.addAttribute("isFoodCollectionDay", isFoodCollectionDay);
        
        // Check if user has pooled for the food date (from today's survey)
        boolean pooledForFoodDate = foodPoolService.hasPooledForFoodDate(employeeId, foodDate);
        model.addAttribute("hasPooled", pooledForFoodDate);
        
        // Check if user has pool registration for TODAY (for collection)
        boolean hasPoolForToday = foodPoolService.hasPooledForFoodDate(employeeId, today);
        model.addAttribute("hasPoolForToday", hasPoolForToday);
        
        // Check if already collected today
        boolean collected = foodPoolService.hasCollectedForFoodDate(employeeId, today);
        model.addAttribute("hasCollected", collected);
        
        // Can collect if today is food collection day (regardless of voting)
        model.addAttribute("canCollectToday", isFoodCollectionDay && !collected);
        
        if (pooledForFoodDate) {
            foodPoolService.getPoolForFoodDate(employeeId, foodDate).ifPresent(pool -> {
                model.addAttribute("pooledType", pool.getFoodType());
            });
        }
        
        // For collection day - get pool info if voted
        if (hasPoolForToday) {
            foodPoolService.getPoolForFoodDate(employeeId, today).ifPresent(pool -> {
                model.addAttribute("collectPooledType", pool.getFoodType());
            });
        }
        
        return "home";
    }
    
    @GetMapping("/pool")
    public String pool(@AuthenticationPrincipal OAuth2User user, Model model) {
        if (user == null) return "redirect:/";
        
        String employeeId = user.getAttribute("employeeId");
        String employeeName = user.getAttribute("employeeName");
        
        model.addAttribute("employeeId", employeeId);
        model.addAttribute("employeeName", employeeName);
        
        LocalDate today = LocalDate.now();
        
        // Get menu
        MenuConfig menu = foodPoolService.getTodayMenu();
        model.addAttribute("menu", menu);
        
        // Food date from survey
        LocalDate foodDate = menu.getFoodDate() != null ? menu.getFoodDate() : today.plusDays(1);
        model.addAttribute("foodDate", foodDate);
        model.addAttribute("foodDateFormatted", foodDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy")));
        
        // Check if already pooled for the food date
        boolean alreadyPooled = foodPoolService.hasPooledForFoodDate(employeeId, foodDate);
        model.addAttribute("alreadyPooled", alreadyPooled);
        
        if (alreadyPooled) {
            foodPoolService.getPoolForFoodDate(employeeId, foodDate).ifPresent(pool -> {
                model.addAttribute("pooledType", pool.getFoodType());
            });
        }
        
        return "pool";
    }
    
    @GetMapping("/scan")
    public String scan(@AuthenticationPrincipal OAuth2User user, Model model) {
        if (user == null) return "redirect:/";
        
        String employeeId = user.getAttribute("employeeId");
        String employeeName = user.getAttribute("employeeName");
        
        model.addAttribute("employeeId", employeeId);
        model.addAttribute("employeeName", employeeName);
        
        LocalDate today = LocalDate.now();
        model.addAttribute("today", today);
        model.addAttribute("todayFormatted", today.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy")));
        
        // Check if today is a food collection day
        boolean isFoodCollectionDay = foodPoolService.isFoodCollectionDay();
        model.addAttribute("isFoodCollectionDay", isFoodCollectionDay);
        
        // Check if user voted for today (as food date)
        boolean hasPooled = foodPoolService.hasPooledForFoodDate(employeeId, today);
        model.addAttribute("hasPooled", hasPooled);
        
        // Check if already collected today
        boolean collected = foodPoolService.hasCollectedForFoodDate(employeeId, today);
        model.addAttribute("hasCollected", collected);
        
        if (hasPooled) {
            foodPoolService.getPoolForFoodDate(employeeId, today).ifPresent(pool -> {
                model.addAttribute("pooledType", pool.getFoodType());
            });
        }
        
        return "scan";
    }
}
