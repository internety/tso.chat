package tso.chat.exceptions;

/**
 * Thrown when the Uplay server is reported to be down.
 */
public class UplayDownException extends Exception {

    public UplayDownException() {
    }

    public UplayDownException(String message) {
        super(message);
    }

    public UplayDownException(String message, Throwable cause) {
        super(message, cause);
    }

    public UplayDownException(Throwable cause) {
        super(cause);
    }

    public UplayDownException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
