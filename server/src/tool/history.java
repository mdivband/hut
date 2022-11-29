package tool;
import java.util.Arrays;

public class history {
    private int dimX;
    private int dimY;
    private int currentSize; //Current Circular Queue Size
    private double[][][] circularQueueElements;
    private int maxSize; //Circular Queue maximum size

    private int rear;//rear position of Circular queue(new element enqueued at rear).
    private int front; //front position of Circular queue(element will be dequeued from front).

    public history(int maxSize, int dimX, int dimY) {
        this.maxSize = maxSize;
        this.dimX = dimX;
        this.dimY = dimY;
        circularQueueElements = new double[maxSize][dimX][dimY];
        currentSize = 0;
        front = -1;
        rear = -1;
    }

    /**
     * Enqueue elements to rear.
     *
     * @return
     */
    public double[][] enqueue(double[][] item) throws QueueFullException {
        if (isFull()) {
            //throw new QueueFullException("Circular Queue is full. Element cannot be added");
            return dequeue();
        }
        //else {
        rear = (rear + 1) % circularQueueElements.length;
        circularQueueElements[rear] = item;
        currentSize++;

        if (front == -1) {
            front = rear;
        }
        //}
        return null;
    }

    /**
     * Dequeue element from Front.
     */
    public double[][] dequeue() throws QueueEmptyException {
        double[][] deQueuedElement;
        if (isEmpty()) {
            throw new QueueEmptyException("Circular Queue is empty. Element cannot be retrieved");
        }
        else {
            deQueuedElement = circularQueueElements[front];
            circularQueueElements[front] = null;
            front = (front + 1) % circularQueueElements.length;
            currentSize--;
        }
        return deQueuedElement;
    }

    public double[][][] getOrderedElementsAsTensor() {
        double[][][] elementsInOrder = new double[maxSize][dimY][dimX];
        //System.out.println("dimx = " + dimX + ", dimy = " + dimY);
        //System.out.println("front = " + front);
        //System.out.println("===============");
        for (int d=0; d<maxSize; d++) {
            for (int i=0; i<dimX; i++) {
                for (int j=0; j<dimY; j++) {
                    //System.out.println("Adding element at " + ((front+d) % maxSize) + ", i=" + i + ", j=" + j);
                    //System.out.println("odered = " + (maxSize - 1 - d) + " original " +(maxSize + (front-d)));
                    //System.out.println();
                    elementsInOrder[maxSize - 1 - d][j][i] = circularQueueElements[(maxSize + (front-d)) % maxSize][j][i];
                }
            }
            //elementsInOrder[maxSize - 1 - d] = circularQueueElements[(front+d) % maxSize];

        }
        //System.out.println("done: " + Arrays.toString(elementsInOrder));

        return elementsInOrder;
    }

    public int getFront() {
        return front;
    }

    /**
     * Check if queue is full.
     */
    public boolean isFull() {
        return (currentSize == circularQueueElements.length);
    }

    /**
     * Check if Queue is empty.
     */
    public boolean isEmpty() {
        return (currentSize == 0);
    }

    @Override
    public String toString() {
        return "CircularQueue [" + Arrays.toString(circularQueueElements) + "]";
    }

}

class QueueFullException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public QueueFullException() {
        super();
    }

    public QueueFullException(String message) {
        super(message);
    }

}

class QueueEmptyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public QueueEmptyException() {
        super();
    }

    public QueueEmptyException(String message) {
        super(message);
    }

}
