package org.example;

import java.util.*;

public class ClassHasTypeVariable<E> {

    E e;
    ArrayList<E> list;
    HashMap<String, E> map;
    HashMap<String, ArrayList<E>> map2;
    HashMap<String, String> map3;

    public <T> void add(T t) {}

    public void add2(E e) {}
}