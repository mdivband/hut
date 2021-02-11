package server.model;

import java.io.Serializable;

public abstract class IdObject implements Serializable {

    private static final long serialVersionUID = 1L;
    private final String id;

    public IdObject(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "_" + id;
    }

}

