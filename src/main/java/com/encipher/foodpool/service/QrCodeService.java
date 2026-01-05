package com.encipher.foodpool.service;

import com.encipher.foodpool.model.QrCode;
import com.encipher.foodpool.repository.QrCodeRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class QrCodeService {
    
    private final QrCodeRepository qrCodeRepository;
    private final AuditService auditService;
    
    private static final int QR_WIDTH = 300;
    private static final int QR_HEIGHT = 300;
    
    /**
     * Generate a new QR code for a food date
     * @param foodDate The date food will be served
     * @param expirationHours How many hours until the QR expires (0 = no expiration)
     * @param generatorEmail Email of admin/contributor generating the QR
     * @param generatorName Name of admin/contributor generating the QR
     */
    public QrCode generateQrCode(LocalDate foodDate, int expirationHours, 
                                  String generatorEmail, String generatorName) {
        String token = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = expirationHours > 0 ? now.plusHours(expirationHours) : null;
        
        QrCode qrCode = QrCode.builder()
                .token(token)
                .foodDate(foodDate)
                .generatedAt(now)
                .expiresAt(expiresAt)
                .generatedBy(generatorEmail)
                .generatedByName(generatorName)
                .isActive(true)
                .build();
        
        QrCode saved = qrCodeRepository.save(qrCode);
        
        // Audit log
        auditService.logQrGenerated(generatorEmail, generatorName, foodDate, expirationHours);
        
        log.info("QR code generated for food date {} by {}, expires: {}", 
                foodDate, generatorEmail, expiresAt);
        
        return saved;
    }
    
    /**
     * Generate QR code image as PNG bytes
     */
    public byte[] generateQrImage(QrCode qrCode) throws WriterException, IOException {
        String qrData = qrCode.getQrData();
        
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.MARGIN, 2);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(qrData, BarcodeFormat.QR_CODE, QR_WIDTH, QR_HEIGHT, hints);
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
        
        return outputStream.toByteArray();
    }
    
    /**
     * Generate QR code image by ID
     */
    public byte[] generateQrImageById(String qrCodeId) throws Exception {
        QrCode qrCode = qrCodeRepository.findById(qrCodeId)
                .orElseThrow(() -> new Exception("QR code not found"));
        return generateQrImage(qrCode);
    }
    
    /**
     * Validate a scanned QR code
     * @return QrCode if valid, null if invalid
     */
    public QrCode validateQrCode(String qrData) {
        String[] parsed = QrCode.parseQrData(qrData);
        if (parsed == null) {
            log.warn("Invalid QR data format: {}", qrData);
            return null;
        }
        
        String foodDateStr = parsed[0];
        String token = parsed[1];
        
        Optional<QrCode> qrCodeOpt = qrCodeRepository.findByToken(token);
        if (qrCodeOpt.isEmpty()) {
            log.warn("QR token not found: {}", token);
            return null;
        }
        
        QrCode qrCode = qrCodeOpt.get();
        
        // Check if active
        if (!qrCode.isActive()) {
            log.warn("QR code is deactivated: {}", token);
            return null;
        }
        
        // Check expiration
        if (!qrCode.isValid()) {
            log.warn("QR code is expired: {}", token);
            return null;
        }
        
        // Verify food date matches
        if (!qrCode.getFoodDate().toString().equals(foodDateStr)) {
            log.warn("QR food date mismatch: expected {}, got {}", qrCode.getFoodDate(), foodDateStr);
            return null;
        }
        
        return qrCode;
    }
    
    /**
     * Validate QR and check if it's for today's food collection
     */
    public boolean validateQrForToday(String qrData) {
        QrCode qrCode = validateQrCode(qrData);
        if (qrCode == null) return false;
        return qrCode.getFoodDate().equals(LocalDate.now());
    }
    
    /**
     * Get active QR codes for a food date
     */
    public List<QrCode> getActiveQrCodes(LocalDate foodDate) {
        return qrCodeRepository.findByFoodDateAndIsActiveTrueOrderByGeneratedAtDesc(foodDate);
    }
    
    /**
     * Get all QR codes for a food date
     */
    public List<QrCode> getQrCodesForDate(LocalDate foodDate) {
        return qrCodeRepository.findByFoodDateOrderByGeneratedAtDesc(foodDate);
    }
    
    /**
     * Get all currently valid QR codes
     */
    public List<QrCode> getValidQrCodes() {
        return qrCodeRepository.findByIsActiveTrueAndExpiresAtAfterOrderByGeneratedAtDesc(LocalDateTime.now());
    }
    
    /**
     * Get all active QR codes
     */
    public List<QrCode> getAllActiveQrCodes() {
        return qrCodeRepository.findByIsActiveTrueOrderByGeneratedAtDesc();
    }
    
    /**
     * Get QR code by ID
     */
    public Optional<QrCode> getQrCodeById(String id) {
        return qrCodeRepository.findById(id);
    }
    
    /**
     * Deactivate a QR code
     */
    public QrCode deactivateQrCode(String id, String deactivatedBy) throws Exception {
        QrCode qrCode = qrCodeRepository.findById(id)
                .orElseThrow(() -> new Exception("QR code not found"));
        
        qrCode.setActive(false);
        qrCode.setDeactivatedBy(deactivatedBy);
        qrCode.setDeactivatedAt(LocalDateTime.now());
        
        QrCode saved = qrCodeRepository.save(qrCode);
        
        log.info("QR code {} deactivated by {}", id, deactivatedBy);
        
        return saved;
    }
    
    /**
     * Scheduled task to auto-deactivate expired QR codes
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void cleanupExpiredQrCodes() {
        List<QrCode> expired = qrCodeRepository.findByExpiresAtBeforeAndIsActiveTrue(LocalDateTime.now());
        
        for (QrCode qrCode : expired) {
            qrCode.setActive(false);
            qrCode.setDeactivatedBy("SYSTEM_EXPIRED");
            qrCode.setDeactivatedAt(LocalDateTime.now());
            qrCodeRepository.save(qrCode);
            log.info("Auto-deactivated expired QR code: {}", qrCode.getId());
        }
        
        if (!expired.isEmpty()) {
            log.info("Cleaned up {} expired QR codes", expired.size());
        }
    }
    
    /**
     * Get expiration options for UI dropdown
     */
    public static Map<Integer, String> getExpirationOptions() {
        Map<Integer, String> options = new LinkedHashMap<>();
        options.put(1, "1 hour");
        options.put(6, "6 hours");
        options.put(12, "12 hours");
        options.put(24, "1 day");
        options.put(168, "1 week");
        options.put(720, "1 month (30 days)");
        options.put(0, "No expiration");
        return options;
    }
}

