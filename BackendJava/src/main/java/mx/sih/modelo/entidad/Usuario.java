/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package mx.sih.modelo.entidad;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(length = 50, nullable = false)
    private String usuario;

    @Column(name = "password_hash", length = 255, nullable = false)
    private String passwordHash;

    @Column(name = "nombre_completo", length = 150, nullable = false)
    private String nombreCompleto;

    @Column(length = 100, unique = true)
    private String email;

    @Column(name = "foto_url", length = 255)
    private String fotoUrl;

    private Boolean activo = true;

    @Column(name = "creado")
    private LocalDateTime creado = LocalDateTime.now();

    @Column(name = "ultimo_acceso")
    private LocalDateTime ultimoAcceso;

    @Column(name = "google_sub", length = 255)
    private String googleSub;

    /**
     * Contador que viaja dentro del JWT (reclamo "tv"). El filtro lo compara con el de la base en
     * cada peticion: si no coinciden, el token ya no vale.
     *
     * <p>Se incrementa al cerrar sesion y al cambiar o restablecer la contrasena, de modo que TODOS
     * los tokens emitidos antes dejen de servir al instante, en todos los dispositivos del usuario.
     * Es lo que un JWT no puede hacer por si solo, porque no tiene estado (H-07 de la validacion).
     */
    @Column(name = "token_version", nullable = false)
    private Integer tokenVersion = 0;

    // Relación con UsuarioEscuelaRol (uno a muchos)
    @OneToMany(mappedBy = "usuario", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<UsuarioEscuelaRol> relacionesEscuelaRol = new HashSet<>();
}