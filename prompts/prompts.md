# Prompts utilizados con la IA

Selección de los prompts usados durante el desarrollo de la práctica con Claude Code, en orden
cronológico. Se han escrito completas las abreviaturas y se han omitido los logs pegados.

## 1. 16/10/2026

que es la fase 0, ya la acabaste?

## 2. 16/10/2026

no entiendo, mira yo sé usar spring boot pero no entiendo la estructura que quieres hacer. Explícamelo de forma sencilla sin irte por las ramas

## 3. 16/10/2026

vale y que es WM_FO y entonces cada Watering Station es el conjunto de WS_Engine + WS_Monitor, estos dos últimos se comunican con sockets

## 4. 17/10/2026

no entiendo nada, ni de sockets ni nada

## 5. 17/10/2026

vale pero hasta donde yo vi de arquitectura hexagonal había domain, application e infrastructure. Por qué no has puesto infraestructure y que quiere decir adapter?

## 6. 17/10/2026

sí, renómbralo a infrastructure y commitea. Para repasar corrígeme si me equivoco, domain es el núcleo de la app donde están las entidades básicas que luego en application es donde estan los mappers y código que sirve de conexión entre domain e infraestructure que este último es donde está la parte más externa de la app lo que se puede cambiar una cosa por otra modular y que no pase nada en la app, es decir, si el día de mañana cambiamos de bbdd tocaríamos en infraestructure y en teoría si está bien  hecha la arquitecture hexagonal no habría mucho problema

## 7. 30/10/2026

cuales son esos casos de uso? explícamelos brevemente
