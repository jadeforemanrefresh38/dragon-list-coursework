package ru.dragonlist.tags;

public enum Tag {
  IMPORTANT("Важное"),
  IDEAS("Идеи"),
  STUDY("Учеба"),
  PERSONAL("Личное");
  private final String label;

  Tag(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
