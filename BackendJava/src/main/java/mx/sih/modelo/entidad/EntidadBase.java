/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package mx.sih.modelo.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;


@MappedSuperclass
@FilterDef(name = "filtroEscuela", parameters = @ParamDef(name = "idEscuela", type = Long.class))
@Filter(name = "filtroEscuela", condition = "escuela_id = :idEscuela")
public abstract class EntidadBase {

    @Column(name = "escuela_id", nullable = false)
    private Long escuelaId;

    // getter y setter
    public Long getEscuelaId() { return escuelaId; }
    public void setEscuelaId(Long escuelaId) { this.escuelaId = escuelaId; }
}