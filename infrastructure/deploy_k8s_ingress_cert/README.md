# Развертывание инфраструктуры k8s с использованием Terraform

## Предварительные требования:

1. Установленный Terraform
2. Установленный Google Cloud SDK
3. Активный аккаунт Google Cloud с админискими правами
4. Включение API конфигураций в Google Cloud
- Kubernetes Engine API
- Compute Engine API
- Cloud Build API
- Cloud Resource Manager API 


### Описание:
Эта папка содержит конфигурационные файлы Terraform для развертывания полной инфраструктуры k8s, nginx ingress controller, cert manager в Google Cloud Platform (GCP).

**Включены следующие шаги:**

1. Создание сервисной учётки для создания и управления k8s кластером с помощью Google cli.
2. Предоставление прав сервисной учётке с помощью Google cli.
3. Создание json ключа для доступа, добавление в рабочую папку и добавление пути.
4. Создание бакета для хранения состояния Terraform.
5. Настройка бекенда для хранения tfstate в созданном бакете.
6. Развертывание Kubernetes кластера (GKE) в GCP.
7. Установка Ingress NGINX Controller в Kubernetes кластер.
8. Установка cert-manager в Kubernetes кластер.
9. Установка cert-manager config в Kubernetes кластер.
10. Подключение к кластеру и проверка установленных зависимостей.

**Установка конфигурации:**

1. Создание сервисной учётки для создания и управления k8s кластером с помощью Google cli
```
gcloud iam service-accounts create <name account> 
" --display-name "Admin access for k8s and bucket diplom"
```

2. Предоставление прав сервисной учётке с помощью Google cli
```
gcloud projects add-iam-policy-binding <название проекта> \
    --member="serviceAccount:<name account>@<название проекта>.iam.gserviceaccount.com" \
    --role="roles/storage.admin"

gcloud projects add-iam-policy-binding <название проекта> \
    --member="serviceAccount:<name account>@<название проекта>.iam.gserviceaccount.com" \
    --role="roles/container.admin"

gcloud projects add-iam-policy-binding <название проекта> \
    --member="serviceAccount:<name account>@<название проекта>.iam.gserviceaccount.com" \
    --role="roles/compute.networkAdmin"

gcloud projects add-iam-policy-binding <название проекта> \
    --member="serviceAccount:<name account>@<название проекта>.iam.gserviceaccount.com" \
    --role="roles/iam.serviceAccountUser"

gcloud projects add-iam-policy-binding <название проекта> \
  --member="serviceAccount:<name account>@<название проекта>.iam.gserviceaccount.com" \
  --role="roles/monitoring.viewer"

gcloud projects add-iam-policy-binding <название проекта> \
  --member="serviceAccount:<name account>@<название проекта>.iam.gserviceaccount.com" \
  --role="roles/monitoring.metricWriter"
```

3. Создание json ключа для доступа, добавление в рабочую папку и добавление пути.
Создать ключ в веб-интерфес или в google cli. Положить ключ в текущию папку. Прописать ключ в фаиле 0_provider.tf
```
gcloud iam service-accounts keys create ~/<название ключа>.json --iam-account=<name account>@<название проекта>.iam.gserviceaccount.com
```

4. Создание бакета для хранения состояния Terraform.
```
terraform init
terraform apply -target=google_storage_bucket.diplom-06-2024
```

5. Настройка бекенда для хранения tfstate в созданном бакете.\
`terraform init` (Команда terraform init теперь инициализирует бэкенд GCS и запросит вас переместить существующий файл состояния в новый бакет. Ответьте "yes" на этот запрос.)

6. Развертывание Kubernetes кластера (GKE) в GCP.
```
terraform apply -target=google_project_service.container \
                -target=google_compute_network.diplom_gke_network \
                -target=google_compute_subnetwork.diplom_gke_subnetwork \
                -target=google_compute_router.diplom_router \
                -target=google_compute_router_nat.diplom_nat \
                -target=google_container_cluster.diplom_gke_cluster \
                -target=google_container_node_pool.diplom_gke_nodes
```

7. Установка Ingress NGINX Controller в Kubernetes кластер.
```
terraform apply -target=helm_release.nginx_ingress
```

8. Установка cert-manager в Kubernetes кластер.
```
terraform apply -target=null_resource.apply_cert_manager_yaml
```

9. Установка cert-manager config в Kubernetes кластер.
```
kubectl apply -f 5_cert-m-config-prod.yaml
```
10. Подключение к кластеру и проверка установленных зависимостей.
```
gcloud container clusters get-credentials <название кластера> --region <название региона> --project <название проекта>
```
Проверка установленного Ingress NGINX Controller
```
kubectl get svc
```

Проверка установленного cert-manager
```
kubectl get -namespace cert-manager
```
