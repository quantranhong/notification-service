package com.notification.api;

import com.notification.domain.Channel;
import com.notification.entity.NotificationTemplate;
import com.notification.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final NotificationTemplateRepository templateRepository;

    @PostMapping
    public ResponseEntity<NotificationTemplate> create(@RequestBody NotificationTemplate template) {
        return ResponseEntity.ok(templateRepository.save(template));
    }

    @GetMapping
    public List<NotificationTemplate> list(@RequestParam(required = false) Channel channel) {
        if (channel != null) return templateRepository.findByChannel(channel);
        return templateRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationTemplate> get(@PathVariable UUID id) {
        return templateRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
