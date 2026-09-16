import { LoginResponse } from "../types";
import api from "./axiosConfig";

export const authService = {
  login: (correo: string, contrasenia: string) =>
    api.post<LoginResponse>('/auth/login', { correo, contrasenia }),

  recuperarPassword: (correo: string) =>
    api.post('/auth/recuperar-password', { correo }),

  restablecerPassword: (token: string, nuevaPassword: string) =>
    api.post('/auth/restablecer-password', { token, nuevaPassword }),
};