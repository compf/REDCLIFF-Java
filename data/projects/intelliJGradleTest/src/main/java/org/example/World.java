package main.java.org.example;

import main.java.org.example.dogData.DogHelper.MySecondByte;

public class World {
    private String[][][] world;
    public void setBlock(int x,int y,int z,int r,int g,int b){
        world[x][y][z]=r+"/"+b+"/"+g;
    }
    public void printDarkerColor(int r,int g, int b){
        r/=2;
        g/=2;
        b/=2;
        System.out.println(r+"/"+b+"/"+g);
        MySecondByte bt=new MySecondByte();
        bt.b3=true;


    }
    public static class MyByte{
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
