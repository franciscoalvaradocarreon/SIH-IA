package mx.sih.modelo.entidad;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "rol_menu", schema = "sih")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@IdClass(RolMenuId.class)
public class RolMenu {

    @Id
    @ManyToOne
    @JoinColumn(name = "rol_id", nullable = false)
    private Rol rol;

    @Id
    @ManyToOne
    @JoinColumn(name = "menu_id", nullable = false)
    private Menu menu;
}
