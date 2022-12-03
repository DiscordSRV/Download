package dev.vankka.dsrvdownloader.util;

public final class Hex {

    private Hex() {}

    public static String toHexString(byte[] input) {
        StringBuilder hexString = new StringBuilder(2 * input.length);
        for (byte b : input) {
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
