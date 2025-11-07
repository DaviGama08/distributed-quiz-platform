package pt.isec.server.services.auth;

public final class Validators {
    private Validators() {}

    public static boolean isValidPassword(String password) {
        return password != null && password.matches("^(?=.*[A-Za-z])(?=.*\\d)" +
                "(?=.*[^A-Za-z0-9]).{8,}$");
    }

    public static boolean isValidEmail(String email){
        if (email == null) return false;
        String regex = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        return email.matches(regex);
    }

    public static boolean isValidName(String name) {
        if (name == null || name.isBlank()) return false;
        String regex = "^[A-Za-zÀ-ÖØ-öø-ÿ]+(?: [A-Za-zÀ-ÖØ-öø-ÿ]+)*$";
        return name.matches(regex);
    }
}
