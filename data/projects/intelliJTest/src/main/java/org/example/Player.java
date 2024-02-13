package main.java.org.example;

import main.java.org.example.World.MyByte;

public class Player {
    private int x,y,z;
    private String name;
    public void teleport(int x, int y, int z) {
        this.x = x+1;
        this.y = y;
        this.z = z;

        MyByte bt=new MyByte();
        bt.b2=!bt.b0;
        bt.b6=this.x%2==0;
        bt.b4=z%2==1;

    }
    public void walk(){
        x++;
        y++;
        z++;
    }
    public void walkBy(int dx, int dy, int dz){
        x += dx;
        y += dy;
        z += dz;
    }
    public Player(String name){
        this.name = name;
        this.x=0;
        this.y=0;
        this.z=0;
    }
    public Player(String name, int x, int y, int z){
        this.name = name;
        this.x=x;
        this.y=y;
        this.z=z;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
    public int getZ() {
        return z;
    }
}
