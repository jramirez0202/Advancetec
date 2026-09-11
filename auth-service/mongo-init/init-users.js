// Seed de arranque para desarrollo local.
//
// El imagen oficial de Mongo ejecuta automaticamente todo *.js que
// encuentre en /docker-entrypoint-initdb.d SOLO la primera vez que el
// volumen de datos esta vacio (ver docker-compose.yml). No hace falta
// correr nada a mano: con `docker compose up` ya queda esta data lista
// para loguearse contra el SPI.
//
// Password en texto plano para AMBOS usuarios (dev/test unicamente,
// nunca uses este hash en un ambiente real): Test1234!

db = db.getSiblingDB('advancetec_auth');

db.users.insertMany([
  {
    username: 'jdoe',
    email: 'jdoe@advancetec.test',
    firstName: 'Jane',
    lastName: 'Doe',
    passwordHash: '$2a$10$NocJ5kU1RuG/dASw.N0uXOOd3auQdozeoKAsrCWsyIYQeEaKnXAIG',
    enabled: true,
    emailVerified: true,
    roles: ['admin'],
  },
  {
    username: 'asmith',
    email: 'asmith@advancetec.test',
    firstName: 'Alice',
    lastName: 'Smith',
    passwordHash: '$2a$10$NocJ5kU1RuG/dASw.N0uXOOd3auQdozeoKAsrCWsyIYQeEaKnXAIG',
    enabled: true,
    emailVerified: true,
    roles: ['operator'],
  },
]);
