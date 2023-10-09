package org.example;

public class OverridingMethod{

    public void hello() {
        System.out.println("Hello from OverridingMethod");
    }

}

public class OverridingMethodChild extends OverridingMethod{

    public void hello() {
        System.out.println("Hello from Child");
    }

}