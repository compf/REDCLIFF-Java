package main.java.org.example.personData;

import main.java.org.example.interfaces.AgeChecker;
import main.java.org.example.interfaces.DaysCalculator;

import java.util.Calendar;

public class PersonHelper implements AgeChecker, DaysCalculator {
    public boolean isAdult(int birthYear,int birthMonth,int birthDay) {
        Calendar now = Calendar.getInstance();
        int year = now.get(Calendar.YEAR);
        int month = now.get(Calendar.MONTH);
        int day = now.get(Calendar.DAY_OF_MONTH);
        if (year - birthYear > 18) {
            return true;
        } else if (year - birthYear == 18) {
            if (month > birthMonth) {
                return true;
            } else if (month == birthMonth) {
                if (day >= birthDay) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public int getAgeInDays(int birthYear, int birthDay, int birthMonth) {
        return birthYear*365 + birthMonth*30 + birthDay;
    }
}
