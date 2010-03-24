package memeograph.ui;

import memeograph.Tree;

/*
 * The class that graphically represents a Node in our tree
 */
public class Node {
    public double x, y, z;
    public double fx;
    public double vx=0;
    public double opacity = 1;
    public double width = 70;
    public Tree data;
    
    public Node(Tree data, double x, double y)
    {
        this(data, x, y, 0.0);
    }

    public Node(Tree data, double x, double y, double z)
    {
        this.data = data;
        this.x = x;
        this.y = y;
        this.z = z;
    }


    public void setOpacity(double opacity){
	this.opacity = opacity;

    }

    public double getOpacity(){
    	return opacity;
    }
}
