/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package mx.sih.modelo.entidad;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 *
 * @author USER
 */
@Entity
@Table(name = "menu", schema = "sih" )
@Getter
@Setter
public class Menu {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "menu_id")
    private Long menuId;
    
    @Column(name = "pariente_id")
    private Long parienteId;
    
    @Column(name = "nombre")
    private String label;
    
    @Column(name = "ruta")
    private String path;
    
    @Column(name = "icono")
    private String icono;

    @Column(name = "menu_orden")
    private Integer menuOrden;
    
    @Column(name = "nivel")
    private Integer nivel;
    
    @Column(name = "activo")
    private Boolean activo = true;

    @Column(name = "creado")
    private LocalDateTime creado = LocalDateTime.now();

}
