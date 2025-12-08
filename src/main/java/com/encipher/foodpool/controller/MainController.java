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
        
        // Today's menu status
        MenuConfig menu = foodPoolService.getTodayMenu();
        model.addAttribute("foodAvailable", menu.isFoodAvailable());
        model.addAttribute("poolOpen", menu.isPoolOpen());
        
        // Today's registration status
        boolean pooled = foodPoolService.hasPooledToday(employeeId);
        boolean collected = foodPoolService.hasCollectedToday(employeeId);
        
        model.addAttribute("hasPooled", pooled);
        model.addAttribute("hasCollected", collected);
        
        if (pooled) {
            foodPoolService.getPoolForToday(employeeId).ifPresent(pool -> {
                model.addAttribute("pooledType", pool.getFoodType());
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
        
        // Get menu
        MenuConfig menu = foodPoolService.getTodayMenu();
        model.addAttribute("menu", menu);
        
        // Check if already pooled
        boolean alreadyPooled = foodPoolService.hasPooledToday(employeeId);
        model.addAttribute("alreadyPooled", alreadyPooled);
        
        if (alreadyPooled) {
            foodPoolService.getPoolForToday(employeeId).ifPresent(pool -> {
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
        
        // Check pool status
        boolean pooled = foodPoolService.hasPooledToday(employeeId);
        boolean collected = foodPoolService.hasCollectedToday(employeeId);
        
        model.addAttribute("hasPooled", pooled);
        model.addAttribute("hasCollected", collected);
        
        if (pooled) {
            foodPoolService.getPoolForToday(employeeId).ifPresent(pool -> {
                model.addAttribute("pooledType", pool.getFoodType());
            });
        }
        
        return "scan";
    }
}
