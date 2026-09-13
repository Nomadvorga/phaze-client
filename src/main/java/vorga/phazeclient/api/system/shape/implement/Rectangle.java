package vorga.phazeclient.api.system.shape.implement;

import vorga.phazeclient.api.system.shape.batched.BatchedRectangle;
import vorga.phazeclient.base.QuickImports;
import vorga.phazeclient.api.system.shape.Shape;
import vorga.phazeclient.api.system.shape.ShapeProperties;

public class Rectangle implements Shape, QuickImports {

    @Override
    public void render(ShapeProperties shape) {
        BatchedRectangle.submit(shape);
    }
}
