up:
    docker compose up -d --build

up-local:
    docker compose -f docker-compose.local.yml up -d

down:
    docker compose down

restart:
    just down && just up

logs:
    docker compose logs -f

ps:
    docker compose ps