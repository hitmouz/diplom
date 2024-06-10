# Развертывание kube-prometheus-stack базовой конфигурации в k8s с использованием Helm chart

## Предварительные требования:

1. Установленный Helm
2. Установленный Google Cloud SDK
3. Активный аккаунт Google Cloud с правами доступа в k8s

### Описание:
Описан процесс установки kube-prometheus-stack базовой конфигурации в k8s с использованием Helm chart
Эта папка содержит фаил values для добавления своих переменных для установки.

**Установка:**

1. Указать данные для развертывания kube-prometheus-stack в фаиле `values.yaml`
```
   namespaceOverride:               название namespace
   cert-manager.io/cluster-issuer:  название конфига cert-manager
   hosts:                           название доменного имени
   secretName:                      имя секрета, где будет хранится сертификат TLS
   adminPassword:                   пароль для Grafana
```
2. Создать namespace в k8s
```
   kubectl create namespace monitoring
```
3. Добавить репозиторий Helm и обновить информацию о репозитории Helm
```
   helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
   helm repo update
```
4. Запустить установку kube-prometheus-stack с нашими переменными
```
   helm install my-prometheus-stack prometheus-community/kube-prometheus-stack -f values.yaml
```
