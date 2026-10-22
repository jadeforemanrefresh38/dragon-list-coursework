package ru.dragonlist.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class WorkspaceController {
  @Value("${app.donation-url:}")
  private String donation;

  @GetMapping("/")
  public String root() {
    return "redirect:/notes";
  }

  @GetMapping("/notes")
  public String workspace(Model model) {
    model.addAttribute("donationUrl", donation.startsWith("https://") ? donation : "");
    return "app";
  }

  @GetMapping({"/notes/{id}", "/notes/{id}/edit"})
  public String note(@PathVariable long id) {
    return "redirect:/notes#" + id;
  }
}
