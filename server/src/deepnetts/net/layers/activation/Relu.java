package deepnetts.net.layers.activation;

import java.io.Serializable;

/**
 * Rectified Linear Activation and its Derivative.
 * 
 * y = max(0, x)
 *        - 
 *       | 1, x > 0
 * y' = <
 *       | 0, 0x<=0 
 *        -
 * 
 * @author Zoran Sevarac
 */
public final class Relu implements ActivationFunction, Serializable {

    @Override
    public float getValue(final float x) {
        return Math.max(0, x);  
    }

    @Override
    public float getPrime(final float y) {
         return ( y > 0 ? 1 : 0);
    }
    
}
