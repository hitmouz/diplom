# Развертывание ELK в k8s

## Предварительные требования:

1. Установленный Helm
2. Установленный Google Cloud SDK
3. Активный аккаунт Google Cloud с правами доступа в k8s

### Описание:
Описан процесс установки стека ELK состоящик из следующих компонентов Elastic Cloud on Kubernetes, Elasticsearch cluster, Kibana.

**Установка:**

1. Install custom resource definitions
```
kubectl create -f https://download.elastic.co/downloads/eck/2.13.0/crds.yaml
```
2. Install the operator with its RBAC rules
```
kubectl apply -f https://download.elastic.co/downloads/eck/2.13.0/operator.yaml
```
3. Deploy an Elasticsearch cluster
```
cat <<EOF | kubectl apply -f -
apiVersion: elasticsearch.k8s.elastic.co/v1
kind: Elasticsearch
metadata:
  name: quickstart
spec:
  version: 8.14.1
  nodeSets:
  - name: default
    count: 1
    config:
      node.store.allow_mmap: false
EOF
```
4. Get an overview of the current Elasticsearch clusters in the Kubernetes cluster, including health, version and number of nodes
```
kubectl get elasticsearch
```
5. Check ClusterIP Service
```
kubectl get service quickstart-es-http
```
6. Install Kibana
```
cat <<EOF | kubectl apply -f -
apiVersion: kibana.k8s.elastic.co/v1
kind: Kibana
metadata:
  name: quickstart
spec:
  version: 8.14.1
  count: 1
  elasticsearchRef:
    name: quickstart
EOF
```
7. Monitor Kibana health and creation progress.
```
kubectl get kibana
```
8. Install Kibana ingress nginx
```
kubectl apply -f kibana-ingress.yaml
``` 
9. Access Kibana
Login as the elastic user
The password can be obtained with the following command:
```
kubectl get secret quickstart-es-elastic-user -o=jsonpath='{.data.elastic}' | base64 --decode; echo
```