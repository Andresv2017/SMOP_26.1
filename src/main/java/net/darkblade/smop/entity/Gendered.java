package net.darkblade.smop.entity;

/** A mob with a sex, used to gate breeding and to pick a texture. */
public interface Gendered {

    boolean isMale();

    void setMale(boolean male);
}
