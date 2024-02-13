package main.java.org.example.dogData;

import main.java.org.example.interfaces.AgeChecker;
import main.java.org.example.personData.PersonHelper;

import java.util.Calendar;

public class DogHelper implements AgeChecker {
    public boolean isAdult(int birthYear,int birthMonth,int birthDay) {
       birthYear=birthYear-(birthYear- Calendar.getInstance().get(Calendar.YEAR))*7;
       return new PersonHelper().isAdult(birthYear,birthMonth,birthDay);
    }
    public static class MySecondByte{
        public boolean b0;
        public boolean b1;
        public boolean b2;
        public boolean b3;
        public boolean b4;
        public boolean b5;
        public boolean b6;
        public boolean b7;
    }
}
