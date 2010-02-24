package memeograph.ui;

import java.awt.Dimension;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Vector;
import memeograph.Tree;
import memeograph.TreeChangeListener;
import memeograph.graphics.Node;
import processing.core.PApplet;
import processing.core.PFont;

/**
 * Here is the moving eye example from processing
 */
public class MemeoPApplet extends PApplet implements TreeChangeListener, MouseWheelListener{
    static int PADDING = 20;
    static float K = 0.01f; //Spring constant (Along the Y)
    static float M = 0.95f; //Magnet contents (Along the X)
    static float FRICTION = .95f;
    static int MOVE_TICK = 50;

    private Map<Tree, Node> positions;
    private Vector<Vector<Node>> layers;

    private Tree tree;
    private boolean treechanged = false;
    private boolean laidout = false;

    PFont font;

    //Camera Info
    float xpos,ypos,zpos;
    float xdir,ydir,zdir;


    public MemeoPApplet(Tree tree){
        this.tree = tree;
        addMouseWheelListener(this);
    }


    @Override
    public void setup(){
        //Full screen, go big or go home!
        size(1024, 768, P3D);
        background(102);

        font = createFont("SansSerif.bold", 18);
        textFont(font);
        textAlign(CENTER, CENTER);

        //Lets see if we can slow down the tree rendering to 30fps
        frameRate(20);

        xpos = width/2.0f;
        ypos = height/2.0f;
        zpos = (height/2.0f) / tan(PI*60.0f / 360.0f);
        xdir = width/2.0f;
        ydir = height/2.0f;
        zdir = 0;
      //camera(width/2.0f, height/2.0f, (height/2.0f) / tan(PI*60.0f / 360.0f),
             //width/2.0f, height/2.0f, 0, 0, 1, 0);
        camera(xpos, ypos, zpos, xdir, ydir, zdir, 0, 1, 0);
       //smooth();
    }


    @Override
    public void draw(){
        background(102);
        camera(xpos, ypos, zpos, xdir, ydir, zdir, 0, 1, 0);

        //First check if we have to layout this stuff out
        if (!laidout) {
            treechanged = false;
            layout(tree, width/2, PADDING);
        }

        //jiggle our layout
        adjust();

        //Now draw the lines between the nodes
        for (Node n : positions.values()) {
            for (Tree kid : n.data.getChildren()) {
                Node knode = positions.get(kid);
                drawLine(n, knode);
            }
        }

        //Draw the nodes ontop of the lines. Awesome.
        for (Node n : positions.values()) {
            drawNode(n);
        }
    }

    private void drawLine(Node from, Node to){
        line((float)from.x, (float)from.y, (float)to.x, (float)to.y);
    }

    private void drawNode(Node n){
        translate((float)n.x, (float)n.y, 0f);
        fill(255);
        box((float)n.width, 20f, 4f);
        translate(0f, 0f, 3f);
        fill(5);
        text(n.data.getTreeName(), 0f, 0f);
        translate(-(float)n.x, -(float)n.y, -3f);
    }

    private void layout(Tree t, double x, double y){
        positions = new HashMap<Tree,Node>();
        layers = new Vector<Vector<Node>>();
        layers.add(new Vector<Node>());

        Queue<Tree> curr_layer = new LinkedList<Tree>();
        curr_layer.add(t);

        int layer = 0;
        Queue<Tree> next_layer = new LinkedList<Tree>();

        double xposition = x;

        while (!curr_layer.isEmpty() || !next_layer.isEmpty()) {
            if (curr_layer.isEmpty()) {
                layer += 1;
                curr_layer = next_layer;
                next_layer = new LinkedList<Tree>();
                layers.add(new Vector<Node>());
                xposition = x;
            }

            t = curr_layer.remove();
            if (positions.containsKey(t)) continue;

            t.addTreeChangeListener(this);

            Node n = new Node(t, xposition, (layer+1)*50);
            n.width = textWidth(n.data.getTreeName());
            xposition += n.width + 100;

            layers.get(layer).add(n);
            positions.put(t, n);

            for (Tree kid : t.getChildren()) {
                if (!positions.containsKey(kid)) {
                    next_layer.add(kid);
                }
            }
        }

        laidout = true; 
    }

    private double adjust(){
        if (!laidout) return -1;

        double total = 0;

        for (Node n : positions.values()) {
            n.fx = 0;
        }

        //magnets
        for (int i = 1; i < layers.size(); i++) {
            Vector<Node> layer = layers.get(i);

            for(int j = 0; j < layer.size(); j++){
                if (j > 0){
                    Node r = layer.get(j);
                    Node l = layer.get(j-1);
                    double d = (l.x + l.width/2) - (r.x - r.width/2);
                    layer.get(j).fx += 1000.0 / (d*d + 1);
                }

                if (j < layer.size() - 1){
                    Node l = layer.get(j);
                    Node r = layer.get(j+1);
                    double d = l.x + l.width/2 - (r.x - r.width/2);
                    layer.get(j).fx -= 1000.0 / (d*d + 1);
                }
            }
        }

        //springs
        for (Node n : positions.values()) {
            for (Tree kidt : n.data.getChildren()) {
                Node kid = positions.get(kidt);

                // F = -k*d
                double dx = n.x - kid.x;
                double dy = n.y - kid.y;

                double d = Math.sqrt(dx*dx + dy*dy);
                double F = K * d;
                kid.fx += F*dx/d;
            }
        }

        for (int i = 1; i < layers.size(); i++) {
            Vector<Node> layer = layers.get(i);
            for(int j = 0; j < layer.size(); j++){
                Node n = layer.get(j);
                n.vx = n.vx*FRICTION + 1*n.fx;
                double newx = n.x + 0.1*n.vx;
                total+= Math.abs(n.fx);
                n.x = newx;
            }

            // Not too close, okay...
            for(int j = 1; j < layer.size(); j++){
                Node l = layer.get(j-1);
                Node n = layer.get(j);
                if (l.x + l.width/2 + n.width/2 + PADDING > n.x) {
                    n.x = l.x + l.width/2 + n.width/2 + PADDING;
                }
            }
        }

        //System.out.println(total);
        return total;
    }

    public void kidAdded(Tree parent, Tree addedNode) {
        throw new UnsupportedOperationException();
    }

    public void childrenRemoved(Tree parent) {
        throw new UnsupportedOperationException();
    }

    public void dataChanged(Tree parent) {
        throw new UnsupportedOperationException();
    }


    float dtheta = .03f;
    @Override
    public void mouseDragged()
    {
        float dy = pmouseY - mouseY;
        if (dy != 0) {
            float y = (ypos-ydir);
            float z = (zpos-zdir);
            float r = sqrt(y*y + z*z);
            float theta = atan2(y, z);

            float theta_new = theta + ((dy > 0) ? dtheta : (-1*dtheta));
            y = sin(theta_new) * r;
            z = cos(theta_new) * r;
            ypos = ydir + y;
            zpos = zdir + z;
        }

        float dx = pmouseX - mouseX;
        if (dx != 0) {
            float x = (xpos-xdir);
            float z = (zpos-zdir);
            float r = sqrt(x*x + z*z);
            float theta = atan2(z, x);

            float theta_new = theta + ((dx < 0) ? dtheta : (-1*dtheta));
            x = cos(theta_new) * r;
            z = sin(theta_new) * r;
            xpos = xdir + x;
            zpos = zdir + z;
        }
    }

    @Override
    public void keyPressed(){
        char k = (char)key;
        switch(k){
            case 'w':
            case 'W': ydir -= MOVE_TICK; ypos -= MOVE_TICK; break;
            case 'a':
            case 'A': xdir -= MOVE_TICK; xpos -= MOVE_TICK; break;
            case 's':
            case 'S': ydir += MOVE_TICK; ypos += MOVE_TICK; break;
            case 'd':
            case 'D': xdir += MOVE_TICK; xpos += MOVE_TICK; break;
            default: break;
        }
    }

    @Override
    public void mouseMoved(){
    }

    public void mouseWheelMoved(MouseWheelEvent e) {
        int notches = e.getWheelRotation(); //notches goes negative if the
                                            //wheel is scrolled up.

        if (notches < 0) {
            float x = .9f * (xpos-xdir);
            float y = .9f * (ypos-ydir);
            float z = .9f * (zpos-zdir);
            xpos = xdir + x;
            ypos = ydir + y;
            zpos = zdir + z;
        } else if (notches > 0) {
            float x = 1.1f * (xpos-xdir);
            float y = 1.1f * (ypos-ydir);
            float z = 1.1f * (zpos-zdir);
            xpos = xdir + x;
            ypos = ydir + y;
            zpos = zdir + z;
        }
    }
}
