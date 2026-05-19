public class vg {
    public double x;
    public double y;
    public double z;
    public float yaw;
    public float pitch;
    public boolean locationAndAnglesCalled;

    public void setLocationAndAngles(double x, double y, double z, float yaw, float pitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.locationAndAnglesCalled = true;
    }
}
