/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package mx.sih.excepcion;


public class NegocioExcepcion extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final String codigoError;

    public NegocioExcepcion(String mensaje) {
        super(mensaje);
        this.codigoError = null;
    }
    
    public NegocioExcepcion(String codigoError, String mensaje) {
        super(mensaje);
        this.codigoError = codigoError;
    }
    
    /**
     * Constructor con mensaje y causa.
     * @param mensaje Descripción del error.
     * @param causa Causa original de la excepción.
     */
    public NegocioExcepcion(String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.codigoError = null;
    }

    /**
     * Constructor con código de error, mensaje y causa.
     * @param codigoError Código que identifica el tipo de error.
     * @param mensaje Descripción del error.
     * @param causa Causa original de la excepción.
     */
    public NegocioExcepcion(String codigoError, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.codigoError = codigoError;
    }

    /**
     * Obtiene el código de error (si existe).
     * @return Código de error o null si no se especificó.
     */
    public String getCodigoError() {
        return codigoError;
    }

    /**
     * Mensaje ORIGINAL, sin el prefijo [codigoError].
     *
     * Es el que debe mostrarse al usuario final: el código ya viaja en el campo
     * "error" de la respuesta JSON, así que anteponerlo al mensaje solo ensucia
     * lo que ve el usuario ("[credenciales_invalidas] Correo o contraseña incorrectos").
     *
     * @return Mensaje tal cual se construyó la excepción.
     */
    public String getMensajeCrudo() {
        return super.getMessage();
    }

    /**
     * Devuelve un mensaje con formato: [codigoError] mensaje (si hay código).
     * Se conserva para los registros de log, donde el código junto al mensaje
     * es útil para diagnosticar.
     */
    @Override
    public String getMessage() {
        if (codigoError != null) {
            return "[" + codigoError + "] " + super.getMessage();
        }
        return super.getMessage();
    }
}
