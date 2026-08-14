public class fun {
    public static void main(String[] args) {
        int[] prime=new int[100];
        int j=0;
        fun f=new fun();
        for(int i=2; i<100;i++){
            if(f.isprime(i)==1){
                prime[j]=i;
                j++;
            }
        }
        int arr[]={1,2,3,4};
        int input2=3;
        for(int k=0;k<arr.length;k++){
            arr[k]=arr[k]+prime[input2-1];
            System.out.print(arr[k]);
        }


        
    }
    public int isprime(int n){
        if (n <= 1) {
            return 0; // Not prime
        }
        for (int i = 2; i <= Math.sqrt(n); i++) {
            if (n % i == 0) {
                return 0; // Not prime
            }
        }
        return 1; // Is prime
    }
}
