# Используем официальный образ Nginx из Docker Hub
FROM nginx:1.26.0

# Копируем файлы веб-сайта в директорию /usr/share/nginx/html в контейнере
COPY /app/mysite /usr/share/nginx/html

# Установка переменной окружения PORT
ENV PORT=80
