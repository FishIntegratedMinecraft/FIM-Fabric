package fish.crafting.fimfabric.tools;

import fish.crafting.fimfabric.connection.packets.F2IDoLocationEditPacket;
import fish.crafting.fimfabric.connection.packets.F2IDoVectorEditPacket;
import fish.crafting.fimfabric.editor.vector.EditorLocation;
import fish.crafting.fimfabric.editor.vector.EditorVector;
import fish.crafting.fimfabric.rendering.custom.RenderContext3D;
import fish.crafting.fimfabric.rendering.custom.ScreenRenderContext;
import fish.crafting.fimfabric.tools.render.ToolAxis;
import fish.crafting.fimfabric.tools.worldselector.WorldSelector;
import fish.crafting.fimfabric.tools.worldselector.WorldSelectorManager;
import fish.crafting.fimfabric.ui.TexRegistry;
import fish.crafting.fimfabric.util.*;
import fish.crafting.fimfabric.util.render.FadeTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class MoveTool extends CustomTool<Positioned> {

    private final BoundingBox[] axisBoundingBoxes;
    private final List<WorldSelector> axisSelectors = new ArrayList<>();

    private final double handlePartLength = 0.4;
    private final double handlePartWidth = 0.008;
    private final double offsetFromCenter = 0.5;
    private final double headPartWidth = 0.05;
    private final double headPartLength = 0.15;

    private boolean editingPos = false;
    private double editingDistance = 0d;
    //Offset from the center of the vector, used so that axis-selecting won't snap
    private Double editingCoordOffset = null;
    private ToolAxis editingAxis = null;
    private final FadeTracker fadingText = new FadeTracker(0, 0.3, 0.1);
    private Vec3d startPos = null;
    private Positioned lastRendered = null;

    public MoveTool(){
        ToolAxis[] values = ToolAxis.values();
        axisBoundingBoxes = new BoundingBox[values.length];
        for (int i = 0; i < values.length; i++) {
            axisBoundingBoxes[i] = new BoundingBox();
            axisSelectors.add(new Selector(axisBoundingBoxes[i], values[i]));
        }
    }

    @Override
    public void onEnable() {
        stopEditing();
        this.startPos = null;
    }

    private void stopEditing(){
        editingPos = false;
        editingAxis = null;
    }

    private void startEditingPosDirectly(@NotNull Positioned positioned, Vec3d camera){
        this.editingPos = true;
        this.editingDistance = camera.distanceTo(positioned.getPos());
        this.editingAxis = null;
        fadingText.begin();
    }

    private void startEditingPosAxis(@NotNull ToolAxis axis){
        this.editingPos = true;
        this.editingAxis = axis;
        this.editingCoordOffset = null;
    }

    public void vectorClickCallback(Positioned positioned, boolean click){
        if(click) {
            Vec3d camera = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
            startEditingPosDirectly(positioned, camera);
        }else{
            stopEditing();
        }
    }

    @Override
    public void render2D(ScreenRenderContext context, RenderTickCounter counter) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        if(fadingText.isActive()) {
            int clr = fadingText.color(0xFFFFFFFF);
            context.drawCenteredTextWithShadow(
                    "Distance to Object: " + NumUtil.betterNumber(editingDistance),
                    WindowUtil.scaledWidth() / 2,
                    WindowUtil.scaledHeight() * 3 / 4,
                    clr
            );
        }

        if(lastRendered != null && startPos != null) {
            context.drawCenteredTextWithShadow(
                    "Before: " + stringify(startPos),
                    WindowUtil.scaledWidth() / 2,
                    40,
                    0xEE89FF93
            );

            context.drawCenteredTextWithShadow(
                    "After: " + stringify(lastRendered.getPos()),
                    WindowUtil.scaledWidth() / 2,
                    (int) (40 + textRenderer.fontHeight * 1.5),
                    0xEE89C2FF
            );

            context.drawCenteredTextWithShadow(
                    "Press ENTER to confirm edit.",
                    WindowUtil.scaledWidth() / 2,
                    (int) (40 + textRenderer.fontHeight * 3),
                    0xEE89FF93
            );

            if(KeyUtil.isShiftPressed()){
                context.drawCenteredTextWithShadow(
                        "Won't clear and return to IntelliJ",
                        WindowUtil.scaledWidth() / 2,
                        (int) (40 + textRenderer.fontHeight * 4.5),
                        0xAAFF0000
                );
            }
        }
    }

    private String stringify(Vec3d pos){
        return "[" + NumUtil.betterNumber(pos.x) + ", " + NumUtil.betterNumber(pos.y) + ", " + NumUtil.betterNumber(pos.z) + "]";
    }

    @Override
    protected void render(RenderContext3D context, Positioned obj) {
        this.lastRendered = obj;
        if(this.startPos == null) {
            this.startPos = obj.getPos();
        }

        Vec3d camera = context.camera();

        if(MinecraftClient.getInstance().player != null && editingPos){
            Vec3d rot = MinecraftClient.getInstance().player.getRotationVec(context.tickDelta());

            if(editingAxis != null){
                Double coord = getPlaneIntersectionCoordinate(
                        obj,
                        editingAxis,
                        camera,
                        rot);

                if(coord != null){
                    if(this.editingCoordOffset == null){
                        this.editingCoordOffset = coord - VectorUtils.getValueUsingUnitVector(obj.getPos(), editingAxis.unit);
                    }

                    coord -= this.editingCoordOffset;

                    double x = editingAxis == ToolAxis.X ? coord : obj.getPos().x;
                    double y = editingAxis == ToolAxis.Y ? coord : obj.getPos().y;
                    double z = editingAxis == ToolAxis.Z ? coord : obj.getPos().z;

                    obj.setPos(x, y, z);
                }
            }else{
                Vec3d newPos = camera.add(rot.multiply(editingDistance));
                obj.setPos(newPos.x, newPos.y, newPos.z);
            }
        }

        double zoom = calculateToolZoom(camera, obj);

        WorldSelector mainActiveSelector = WorldSelectorManager.get().getMainActiveSelector();
        ToolAxis hoveredAxis = null;
        if(mainActiveSelector instanceof Selector selector) {
            hoveredAxis = selector.axis;
        }

        for (ToolAxis value : ToolAxis.values()) {
            renderHandle(context, value, zoom, obj.getPos(), value.color(hoveredAxis));
            renderHead(context, value, zoom, obj.getPos(), value.color(hoveredAxis));
        }

        for (ToolAxis value : ToolAxis.values()) {
            Vec3d u = value.unit.multiply(zoom * (handlePartLength + headPartLength));

            Vec3d origin = obj.getPos().add(value.unit.multiply((offsetFromCenter - handlePartLength) * zoom));
            Vec3d a = value.oppositeUnit.multiply(headPartWidth * 0.8 * zoom);

            BoundingBox box = axisBoundingBoxes[value.ordinal()];
            box.change(
                    origin.x + a.x,
                    origin.y + a.y,
                    origin.z + a.z,
                    origin.x - a.x + u.x,
                    origin.y - a.y + u.y,
                    origin.z - a.z + u.z);

        }
    }

    @Override
    public boolean isAccessibleFor(Object object) {
        return object instanceof Positioned;
    }

    @Override
    public Identifier getTexture() {
        return TexRegistry.TOOL_MOVE;
    }

    private void renderHandle(RenderContext3D context,
                              ToolAxis facing,
                              double zoom,
                              Vec3d position,
                              int color){
        context.push();
        context.translateCamera();

        RenderContext3D.VertexHelper vertexHelper = context.renderVertices();

        int angles = 9;
        double rotate = Math.TAU / (angles);
        Vec3d unit = facing.unit;

        double x = unit.x * handlePartWidth * zoom;
        double y = unit.y * handlePartWidth * zoom;
        double z = unit.z * handlePartWidth * zoom;

        double xLen = unit.x * handlePartLength * zoom;
        double yLen = unit.y * handlePartLength * zoom;
        double zLen = unit.z * handlePartLength * zoom;

        double anchorX = position.x + unit.x * (offsetFromCenter - handlePartLength) * zoom;
        double anchorY = position.y + unit.y * (offsetFromCenter - handlePartLength) * zoom;
        double anchorZ = position.z + unit.z * (offsetFromCenter - handlePartLength) * zoom;

        for (int i = 0; i < angles; i++) {
            double ang1 = rotate * i;
            double ang2 = rotate * (i + 1);

            double sin1 = Math.sin(ang1);
            double sin2 = Math.sin(ang2);

            double cos1 = Math.cos(ang1);
            double cos2 = Math.cos(ang2);

            vertexHelper.vertex((float) (anchorX + y * sin2 + z * sin2 + xLen),
                            (float) (anchorY + x * sin2 + z * cos2 + yLen),
                            (float) (anchorZ + x * cos2 + y * cos2 + zLen), color);

            vertexHelper.vertex((float) (anchorX + y * sin1 + z * sin1),
                            (float) (anchorY + x * sin1 + z * cos1),
                            (float) (anchorZ + x * cos1 + y * cos1), color);

            vertexHelper.vertex((float) (anchorX + y * sin2 + z * sin2),
                            (float) (anchorY + x * sin2 + z * cos2),
                            (float) (anchorZ + x * cos2 + y * cos2), color);

            vertexHelper.vertex((float) (anchorX + y * sin1 + z * sin1 + xLen),
                            (float) (anchorY + x * sin1 + z * cos1 + yLen),
                            (float) (anchorZ + x * cos1 + y * cos1 + zLen), color);
        }

        context.pop();
    }

    private void renderHead(RenderContext3D context,
                            ToolAxis facing,
                            double zoom,
                            Vec3d position,
                            int color){
        context.push();
        context.translateCamera();

        RenderContext3D.VertexHelper vertexHelper = context.renderVertices();

        int angles = 36;
        double rotate = Math.TAU / (angles);
        Vec3d unit = facing.unit;

        double x = unit.x * headPartWidth * zoom;
        double y = unit.y * headPartWidth * zoom;
        double z = unit.z * headPartWidth * zoom;

        double anchorX = position.x + unit.x * offsetFromCenter * zoom;
        double anchorY = position.y + unit.y * offsetFromCenter * zoom;
        double anchorZ = position.z + unit.z * offsetFromCenter * zoom;

        //sin0 = 0, cos0 = 1
        Vec3d firstAnglePos = new Vec3d(
                0, z, y
        );

        //Render base
        for (int i = 1; i < angles; i++) {
            double ang1 = rotate * i;
            double ang2 = rotate * (i + 1);

            double sin1 = Math.sin(ang1);
            double sin2 = Math.sin(ang2);

            double cos1 = Math.cos(ang1);
            double cos2 = Math.cos(ang2);

            vertexHelper.vertex( (float) (anchorX + firstAnglePos.x),
                            (float) (anchorY + firstAnglePos.y),
                            (float) (anchorZ + firstAnglePos.z), color);

            vertexHelper.vertex( (float) (anchorX + y * sin2 + z * sin2),
                            (float) (anchorY + x * sin2 + z * cos2),
                            (float) (anchorZ + x * cos2 + y * cos2), color);

            vertexHelper.vertex( (float) (anchorX + y * sin1 + z * sin1),
                            (float) (anchorY + x * sin1 + z * cos1),
                            (float) (anchorZ + x * cos1 + y * cos1), color);
        }

        for (int i = 0; i < angles; i++) {
            double ang1 = rotate * i;
            double ang2 = rotate * (i + 1);

            double sin1 = Math.sin(ang1);
            double sin2 = Math.sin(ang2);

            double cos1 = Math.cos(ang1);
            double cos2 = Math.cos(ang2);

            if(facing == ToolAxis.Z) {
                vertexHelper.vertex((float) (anchorX + unit.x * headPartLength * zoom),
                                (float) (anchorY + unit.y * headPartLength * zoom),
                                (float) (anchorZ + unit.z * headPartLength * zoom), color);

                vertexHelper.vertex((float) (anchorX + y * sin2 + z * sin2),
                                (float) (anchorY + x * sin2 + z * cos2),
                                (float) (anchorZ + x * cos2 + y * cos2), color);

                vertexHelper.vertex((float) (anchorX + y * sin1 + z * sin1),
                                (float) (anchorY + x * sin1 + z * cos1),
                                (float) (anchorZ + x * cos1 + y * cos1), color);
            }else if(facing == ToolAxis.Y){
                vertexHelper.vertex((float) (anchorX + y * sin1 + z * sin1),
                                (float) (anchorY + x * sin1 + z * cos1),
                                (float) (anchorZ + x * cos1 + y * cos1), color);

                vertexHelper.vertex((float) (anchorX + y * sin2 + z * sin2),
                                (float) (anchorY + x * sin2 + z * cos2),
                                (float) (anchorZ + x * cos2 + y * cos2), color);

                vertexHelper.vertex((float) (anchorX + unit.x * headPartLength * zoom),
                                (float) (anchorY + unit.y * headPartLength * zoom),
                                (float) (anchorZ + unit.z * headPartLength * zoom), color);
            }else{
                vertexHelper.vertex(
                                (float) (anchorX + unit.x * headPartLength * zoom),
                                (float) (anchorY + unit.y * headPartLength * zoom),
                                (float) (anchorZ + unit.z * headPartLength * zoom), color);
                vertexHelper.vertex(
                                (float) (anchorX + y * sin1 + z * sin1),
                                (float) (anchorY + x * sin1 + z * cos1),
                                (float) (anchorZ + x * cos1 + y * cos1), color);

                vertexHelper.vertex(
                                (float) (anchorX + y * sin2 + z * sin2),
                                (float) (anchorY + x * sin2 + z * cos2),
                                (float) (anchorZ + x * cos2 + y * cos2), color);

            }

        }

        context.pop();
    }

    /**
     * When moving using the axis-arrows, the axis creates a big ass plane that we need to find the intersection
     * of using the player's view direction, and extract the wanted coordinate position :D
     */
    private Double getPlaneIntersectionCoordinate(Positioned positioned,
                                                  ToolAxis planeFacing,
                                                  Vec3d camera,
                                                  Vec3d facing){
        Vec3d vec3d = VectorUtils.lineIntersection(
                positioned.getPos(),
                facing.multiply(planeFacing.oppositeUnit).normalize(),
                camera,
                facing);

        if(vec3d != null){
            return VectorUtils.sumCoords(vec3d.multiply(planeFacing.unit));
        }
        //for (Vec3d normal : planeFacing.planeNormals) {
        //}

        return null;
    }

    @Override
    public boolean onScroll(double scroll) {
        if(!editingPos || editingAxis != null) return false; //Is editing vector directly

        double move = 0.5 * scroll;
        if(KeyUtil.isControlPressed()) move *= 4;
        if(KeyUtil.isShiftPressed()) move *= 0.5;

        if(KeyUtil.isAltPressed() && scroll < 0) {
            editingDistance = 0;
        }else{
            editingDistance += move;
            if(editingDistance < 0) editingDistance = 0;
        }

        fadingText.begin();
        return true;
    }

    @Override
    protected void confirmEdit(Positioned positioned) {
        switch (positioned) {
            case EditorVector vector -> new F2IDoVectorEditPacket(vector).send();
            case EditorLocation location -> new F2IDoLocationEditPacket(location).send();
            default -> {}
        }

        startPos = positioned.getPos();
    }

    @Override
    public List<WorldSelector> getSelectors() {
        return axisSelectors;
    }

    public class Selector extends WorldSelector {

        private final BoundingBox boundingBox;
        private final ToolAxis axis;

        private Selector(BoundingBox box, ToolAxis axis){
            this.boundingBox = box;
            this.axis = axis;
        }

        @Override
        protected void onPress(int button, int mods) {
            startEditingPosAxis(axis);
        }

        @Override
        protected void onUnPress() {
            stopEditing();
        }

        @Override
        public Box getBox() {
            return boundingBox.toMCBox();
        }
    }
}
